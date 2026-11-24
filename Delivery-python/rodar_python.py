import asyncio
import os
import sys

import uvicorn

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(BASE, "cardapio_service"))
sys.path.insert(0, os.path.join(BASE, "pagamento_service"))

# cada módulo lê DB_NAME no momento em que é importado,
# então o nome do banco é definido antes de cada import
os.environ["DB_NAME"] = "delivery_cardapio"
import cardapio_main

os.environ["DB_NAME"] = "delivery_pagamento"
import pagamento_main


async def main():
    cardapio = uvicorn.Server(uvicorn.Config(cardapio_main.app, host="0.0.0.0", port=8001))
    pagamento = uvicorn.Server(uvicorn.Config(pagamento_main.app, host="0.0.0.0", port=8003))
    await asyncio.gather(cardapio.serve(), pagamento.serve())


if __name__ == "__main__":
    asyncio.run(main())