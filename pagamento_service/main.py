"""
Microsserviço de PAGAMENTO (FastAPI + MySQL)

Instalar:   pip install -r requirements.txt
Rodar:      python main.py          (ou: uvicorn main:app --reload --port 8003)
Docs:       http://localhost:8003/docs

Variáveis de ambiente (opcionais, mesmos nomes do AvaliacaoController):
  DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME, PORT

Rotas:
  POST   /pagamentos        registra o pagamento (pix, cartao [credito|debito] ou dinheiro)
  GET    /pagamentos        lista (aceita ?pedidoId=123)
  GET    /pagamentos/{id}   busca um pagamento

Obs.: o pagamento é SIMULADO (não há gateway real). Todo pagamento válido é
aprovado na hora, igual ao mock do front.
"""
import os
from dotenv import load_dotenv
load_dotenv()
from contextlib import asynccontextmanager
from datetime import datetime
from decimal import Decimal
from typing import Literal, Optional
from urllib.parse import quote_plus

from fastapi import Depends, FastAPI, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, model_validator
from sqlalchemy import DateTime, Integer, Numeric, String, create_engine, func, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker
from starlette.exceptions import HTTPException

# ------------------------------------------------------------------ banco
load_dotenv()

# ------------------------------------------------------------------ banco
def _montar_url() -> str:
    url = os.getenv("DATABASE_URL")
    if url:
        url = url.split("?")[0]  # remove parâmetros como ?pgbouncer=true
        for prefixo in ("postgres://", "postgresql://"):
            if url.startswith(prefixo):
                return "postgresql+psycopg2://" + url[len(prefixo):]
        return url
    return (
        "postgresql+psycopg2://"
        f"{quote_plus(os.getenv('DB_USER', 'postgres'))}:{quote_plus(os.getenv('DB_PASSWORD', ''))}"
        f"@{os.getenv('DB_HOST', 'localhost')}:{os.getenv('DB_PORT', '5432')}"
        f"/{os.getenv('DB_NAME', 'postgres')}"
    )

DATABASE_URL = _montar_url()
engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
    pool_size=3,
    max_overflow=2,
    connect_args={"options": "-csearch_path=pagamento"},
)
SessionLocal = sessionmaker(bind=engine)


class Base(DeclarativeBase):
    pass


class Pagamento(Base):
    __tablename__ = "pagamento"
    __table_args__ = {"schema": "pagamento"}

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    pedido_id: Mapped[int] = mapped_column(Integer, unique=True)  # 1 pagamento por pedido
    valor: Mapped[Decimal] = mapped_column(Numeric(10, 2))
    metodo: Mapped[str] = mapped_column(String(20))  # pix | cartao | dinheiro
    tipo_cartao: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)  # credito | debito
    status: Mapped[str] = mapped_column(String(20), default="aprovado")
    criado_em: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())


def get_db():
    with SessionLocal() as db:
        yield db


@asynccontextmanager
async def lifespan(app: FastAPI):
    with engine.begin() as conn:
        conn.execute(text("CREATE SCHEMA IF NOT EXISTS pagamento"))
    Base.metadata.create_all(engine)  # cria pagamento.pagamento
    yield
# ------------------------------------------------------------------ schemas
class PagamentoEntrada(BaseModel):
    """
    Corpo que o front envia: { pedidoId, valor, metodo, tipoCartao? }.

    metodo:     "pix" | "cartao" | "dinheiro"
    tipoCartao: "credito" | "debito"  (só para metodo "cartao"; se não vier, assume "credito")
    """

    pedidoId: int = Field(gt=0)
    valor: float = Field(gt=0, lt=100000000)
    metodo: Literal["pix", "cartao", "dinheiro"]
    tipoCartao: Optional[Literal["credito", "debito"]] = None

    @model_validator(mode="after")
    def checar_cartao(self):
        if self.metodo == "cartao":
            self.tipoCartao = self.tipoCartao or "credito"
        elif self.tipoCartao is not None:
            raise ValueError("tipoCartao só pode ser usado quando metodo for 'cartao'.")
        return self


def para_json(p: Pagamento) -> dict:
    return {
        "ok": p.status == "aprovado",
        "id": p.id,
        "pedidoId": p.pedido_id,
        "valor": float(p.valor),
        "metodo": p.metodo,
        "tipoCartao": p.tipo_cartao,
        "status": p.status,
        "criadoEm": p.criado_em.isoformat() if p.criado_em else None,
    }


# ------------------------------------------------------------------ app
app = FastAPI(title="Serviço de Pagamento", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


# Erros no mesmo formato do AvaliacaoController: { "erro": "mensagem" }
@app.exception_handler(HTTPException)
async def erro_http(request: Request, exc: HTTPException):
    return JSONResponse(status_code=exc.status_code, content={"erro": exc.detail})


@app.exception_handler(RequestValidationError)
async def erro_validacao(request: Request, exc: RequestValidationError):
    primeiro = exc.errors()[0]
    campo = ".".join(str(p) for p in primeiro["loc"][1:]) or "requisição"
    return JSONResponse(status_code=400, content={"erro": f"{campo}: {primeiro['msg']}"})


# ------------------------------------------------------------------ rotas
@app.post("/pagamentos", status_code=201)
def pagar(dados: PagamentoEntrada, db: Session = Depends(get_db)):
    ja_pago = db.scalar(select(Pagamento).where(Pagamento.pedido_id == dados.pedidoId))
    if ja_pago:
        raise HTTPException(409, "Este pedido já foi pago.")

    pagamento = Pagamento(
        pedido_id=dados.pedidoId,
        valor=Decimal(str(round(dados.valor, 2))),  # round: o front soma floats (ex.: 55.400000000000006)
        metodo=dados.metodo,
        tipo_cartao=dados.tipoCartao,
        status="aprovado",
    )
    db.add(pagamento)
    try:
        db.commit()
    except IntegrityError:  # duas requisições ao mesmo tempo para o mesmo pedido
        db.rollback()
        raise HTTPException(409, "Este pedido já foi pago.")
    db.refresh(pagamento)
    return para_json(pagamento)


@app.get("/pagamentos")
def listar(pedidoId: Optional[int] = Query(None), db: Session = Depends(get_db)):
    consulta = select(Pagamento).order_by(Pagamento.criado_em.desc(), Pagamento.id.desc())
    if pedidoId is not None:
        consulta = consulta.where(Pagamento.pedido_id == pedidoId)
    return [para_json(p) for p in db.scalars(consulta)]


@app.get("/pagamentos/{pagamento_id}")
def buscar(pagamento_id: int, db: Session = Depends(get_db)):
    pagamento = db.get(Pagamento, pagamento_id)
    if pagamento is None:
        raise HTTPException(404, "Pagamento não encontrado.")
    return para_json(pagamento)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8003")))