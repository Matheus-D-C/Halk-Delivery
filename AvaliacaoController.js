/**
 * Dependências:
 *   npm install express cors mysql2
 *
 * Variáveis de ambiente (opcionais, com valores padrão para desenvolvimento):
 *   DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME, PORT
 *
 * Rotas:
 *   POST   /avaliacoes        cria uma avaliação
 *   GET    /avaliacoes        lista (aceita ?pedidoId=123)
 *   GET    /avaliacoes/:id    busca uma avaliação
 *   DELETE /avaliacoes/:id    remove uma avaliação
 */
const express = require("express");
const cors = require("cors");
const mysql = require("mysql2/promise");

const SQL_CRIAR_TABELA = `
  CREATE TABLE IF NOT EXISTS avaliacao (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    pedido_id   INT NOT NULL UNIQUE,
    nota        TINYINT NOT NULL,
    comentario  VARCHAR(500) NULL,
    criado_em   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_nota CHECK (nota BETWEEN 1 AND 5)
  )
`;

// banco
const pool = mysql.createPool({
  host: process.env.DB_HOST || "localhost",
  port: Number(process.env.DB_PORT || 3000),
  user: process.env.DB_USER || "root",
  password: process.env.DB_PASSWORD || "",
  database: process.env.DB_NAME || "delivery",
  waitForConnections: true,
  connectionLimit: 10,
});

/** Converte a linha do banco para o formato que o front espera. */
function paraJson(linha) {
  return {
    id: linha.id,
    pedidoId: linha.pedido_id,
    nota: linha.nota,
    comentario: linha.comentario || "",
    criadoEm: linha.criado_em ? new Date(linha.criado_em).toISOString() : null,
  };
}

const erro = (res, status, mensagem) => res.status(status).json({ erro: mensagem });

// ----------------------------------------------------------- validação
/** Devolve { dados } se estiver tudo certo, ou { mensagem } se houver erro. */
function validar(corpo) {
  if (!corpo || typeof corpo !== "object") {
    return { mensagem: "Envie um JSON válido." };
  }

  const { pedidoId, nota } = corpo;
  const comentario = String(corpo.comentario ?? "").trim();

  if (!Number.isInteger(pedidoId) || pedidoId <= 0) {
    return { mensagem: "pedidoId deve ser um número inteiro positivo." };
  }
  if (!Number.isInteger(nota) || nota < 1 || nota > 5) {
    return { mensagem: "nota deve ser um número inteiro de 1 a 5." };
  }
  if (comentario.length > 500) {
    return { mensagem: "comentario deve ter no máximo 500 caracteres." };
  }

  return { dados: { pedidoId, nota, comentario } };
}

// rotas
const router = express.Router();

router.post("/avaliacoes", async (req, res) => {
  const { dados, mensagem } = validar(req.body);
  if (mensagem) return erro(res, 400, mensagem);

  try {
    const [resultado] = await pool.execute(
      "INSERT INTO avaliacao (pedido_id, nota, comentario) VALUES (?, ?, ?)",
      [dados.pedidoId, dados.nota, dados.comentario]
    );
    const [linhas] = await pool.execute("SELECT * FROM avaliacao WHERE id = ?", [
      resultado.insertId,
    ]);
    return res.status(201).json(paraJson(linhas[0]));
  } catch (e) {
    if (e.code === "ER_DUP_ENTRY") {
      return erro(res, 409, "Este pedido já foi avaliado.");
    }
    console.error(e);
    return erro(res, 500, "Erro ao salvar a avaliação.");
  }
});

router.get("/avaliacoes", async (req, res) => {
  const pedidoId = Number.parseInt(req.query.pedidoId, 10);
  try {
    const [linhas] = Number.isInteger(pedidoId)
      ? await pool.execute("SELECT * FROM avaliacao WHERE pedido_id = ?", [pedidoId])
      : await pool.execute("SELECT * FROM avaliacao ORDER BY criado_em DESC");
    return res.json(linhas.map(paraJson));
  } catch (e) {
    console.error(e);
    return erro(res, 500, "Erro ao consultar as avaliações.");
  }
});

router.get("/avaliacoes/:id", async (req, res) => {
  const id = Number.parseInt(req.params.id, 10);
  if (!Number.isInteger(id)) return erro(res, 400, "id inválido.");

  try {
    const [linhas] = await pool.execute("SELECT * FROM avaliacao WHERE id = ?", [id]);
    if (linhas.length === 0) return erro(res, 404, "Avaliação não encontrada.");
    return res.json(paraJson(linhas[0]));
  } catch (e) {
    console.error(e);
    return erro(res, 500, "Erro ao consultar a avaliação.");
  }
});

router.delete("/avaliacoes/:id", async (req, res) => {
  const id = Number.parseInt(req.params.id, 10);
  if (!Number.isInteger(id)) return erro(res, 400, "id inválido.");

  try {
    const [resultado] = await pool.execute("DELETE FROM avaliacao WHERE id = ?", [id]);
    if (resultado.affectedRows === 0) return erro(res, 404, "Avaliação não encontrada.");
    return res.status(204).end();
  } catch (e) {
    console.error(e);
    return erro(res, 500, "Erro ao remover a avaliação.");
  }
});

// execução local
async function iniciar() {
  await pool.query(SQL_CRIAR_TABELA);

  const app = express();
  app.use(cors()); // permite o front (outra porta) chamar este serviço
  app.use(express.json());
  app.use(router);

  const porta = Number(process.env.PORT || 8004);
  app.listen(porta, () => console.log(`Serviço de avaliações rodando na porta ${porta}`));
}

// Roda o servidor só quando o arquivo é executado direto (node AvaliacaoController.js)
if (require.main === module) {
  iniciar().catch((e) => {
    console.error("Falha ao iniciar:", e.message);
    process.exit(1);
  });
}

module.exports = { router, pool };