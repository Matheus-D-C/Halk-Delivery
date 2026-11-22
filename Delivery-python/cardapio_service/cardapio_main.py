"""
Microsserviço de CARDÁPIO (FastAPI + MySQL)

Instalar:   pip install -r requirements.txt
Rodar:      python main.py          (ou: uvicorn main:app --reload --port 3306)
Docs:       http://localhost:3306/docs

Variáveis de ambiente (opcionais, mesmos nomes do AvaliacaoController):
  DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME, PORT

Rotas:
  GET    /itens            lista o cardápio (aceita ?cat=Lanches)
  GET    /itens/{id}       busca um item
  POST   /itens            cria um item
  PUT    /itens/{id}       atualiza um item
  DELETE /itens/{id}       remove um item
"""
import os
from contextlib import asynccontextmanager
from decimal import Decimal
from typing import Optional
from urllib.parse import quote_plus

from fastapi import Depends, FastAPI, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import Numeric, String, create_engine, func, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker
from starlette.exceptions import HTTPException

# ------------------------------------------------------------------ banco
DATABASE_URL = os.getenv("DATABASE_URL") or (
    "mysql+pymysql://"
    f"{quote_plus(os.getenv('DB_USER', 'root'))}:{quote_plus(os.getenv('DB_PASSWORD', ''))}"
    f"@{os.getenv('DB_HOST', 'localhost')}:{os.getenv('DB_PORT', '3306')}"
    f"/{os.getenv('DB_NAME', 'delivery')}?charset=utf8mb4"
)
engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine)


class Base(DeclarativeBase):
    pass


class ItemCardapio(Base):
    __tablename__ = "item_cardapio"
    __table_args__ = {"mysql_charset": "utf8mb4"}  # para guardar emojis

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    nome: Mapped[str] = mapped_column(String(100))
    descricao: Mapped[str] = mapped_column(String(255), default="")
    preco: Mapped[Decimal] = mapped_column(Numeric(10, 2))
    categoria: Mapped[str] = mapped_column(String(50))
    emoji: Mapped[str] = mapped_column(String(16), default="🍽️")


def get_db():
    with SessionLocal() as db:
        yield db



ITENS_INICIAIS = [
    ("X-Burger da Casa", "Pão brioche, blend 160g, queijo e molho especial.", 28.9, "Lanches", "🍔"),
    ("X-Frango Crocante", "Frango empanado, alface, tomate e maionese verde.", 26.5, "Lanches", "🥪"),
    ("Pizza Calabresa", "Média, 8 fatias, cebola roxa e azeitona.", 46, "Pizzas", "🍕"),
    ("Pizza Marguerita", "Média, 8 fatias, tomate, mussarela e manjericão.", 44, "Pizzas", "🍕"),
    ("Combo Sushi 20 peças", "Salmão, atum e kani, com shoyu e gengibre.", 59.9, "Japonês", "🍣"),
    ("Suco de Caju 500ml", "Natural, sem açúcar.", 9, "Bebidas", "🧃"),
    ("Refrigerante Lata", "350ml, vários sabores.", 6, "Bebidas", "🥤"),
    ("Pudim de Leite", "Fatia generosa com calda de caramelo.", 12, "Sobremesas", "🍮"),
]


def semear_cardapio():
    with SessionLocal() as db:
        if db.scalar(select(func.count()).select_from(ItemCardapio)) == 0:
            db.add_all(
                ItemCardapio(nome=n, descricao=d, preco=Decimal(str(p)), categoria=c, emoji=e)
                for n, d, p, c, e in ITENS_INICIAIS
            )
            db.commit()


@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(engine)  # CREATE TABLE IF NOT EXISTS
    semear_cardapio()
    yield


# ------------------------------------------------------------------ schemas
class ItemEntrada(BaseModel):
    """Mesmos nomes de campos que o front usa: nome, desc, preco, cat, e."""

    model_config = ConfigDict(str_strip_whitespace=True)

    nome: str = Field(min_length=1, max_length=100)
    desc: str = Field(default="", max_length=255)
    preco: float = Field(gt=0, lt=100000000)
    cat: str = Field(min_length=1, max_length=50)
    e: str = Field(default="🍽️", max_length=16)


def para_json(item: ItemCardapio) -> dict:
    """Converte a linha do banco para o formato que o front espera."""
    return {
        "id": item.id,
        "nome": item.nome,
        "desc": item.descricao,
        "preco": float(item.preco),
        "cat": item.categoria,
        "e": item.emoji,
    }


def buscar_ou_404(db: Session, item_id: int) -> ItemCardapio:
    item = db.get(ItemCardapio, item_id)
    if item is None:
        raise HTTPException(404, "Item não encontrado.")
    return item


# ------------------------------------------------------------------ app
app = FastAPI(title="Serviço de Cardápio", lifespan=lifespan)
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
@app.get("/itens")
def listar(cat: Optional[str] = Query(None), db: Session = Depends(get_db)):
    consulta = select(ItemCardapio).order_by(ItemCardapio.id)
    if cat:
        consulta = consulta.where(ItemCardapio.categoria == cat)
    return [para_json(i) for i in db.scalars(consulta)]


@app.get("/itens/{item_id}")
def buscar(item_id: int, db: Session = Depends(get_db)):
    return para_json(buscar_ou_404(db, item_id))


@app.post("/itens", status_code=201)
def criar(dados: ItemEntrada, db: Session = Depends(get_db)):
    item = ItemCardapio(
        nome=dados.nome,
        descricao=dados.desc,
        preco=Decimal(str(round(dados.preco, 2))),
        categoria=dados.cat,
        emoji=dados.e,
    )
    db.add(item)
    db.commit()
    db.refresh(item)
    return para_json(item)


@app.put("/itens/{item_id}")
def atualizar(item_id: int, dados: ItemEntrada, db: Session = Depends(get_db)):
    item = buscar_ou_404(db, item_id)
    item.nome = dados.nome
    item.descricao = dados.desc
    item.preco = Decimal(str(round(dados.preco, 2)))
    item.categoria = dados.cat
    item.emoji = dados.e
    db.commit()
    db.refresh(item)
    return para_json(item)


@app.delete("/itens/{item_id}", status_code=204)
def remover(item_id: int, db: Session = Depends(get_db)):
    db.delete(buscar_ou_404(db, item_id))
    db.commit()
    return Response(status_code=204)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8001")))
