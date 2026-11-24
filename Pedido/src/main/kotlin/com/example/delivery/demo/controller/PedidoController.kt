package com.example.cardapio.demo.controller

import com.example.cardapio.demo.client.CarrinhoClient
import com.example.cardapio.demo.model.Pedido
import com.example.cardapio.demo.repository.PedidoRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/pedidos")
class PedidoController(
    private val pedidoRepository: PedidoRepository,
    private val carrinhoClient: CarrinhoClient
) {
    private val statusValidos = listOf("Recebido", "Preparando", "A caminho", "Entregue")

    @GetMapping
    fun listarTodos(@RequestParam(required = false) pago: Boolean?): List<PedidoResponse> {
        val pedidos = if (pago != null) pedidoRepository.findByPago(pago) else pedidoRepository.findAll()
        return pedidos.sortedByDescending { it.id }.map { paraResponse(it) }
    }

    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long): PedidoResponse = paraResponse(buscarEntidade(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(@RequestBody request: CriarPedidoRequest): PedidoResponse {
        if (pedidoRepository.existsByCarrinhoId(request.carrinhoId)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT, "Já existe um pedido criado para o carrinho ${request.carrinhoId}"
            )
        }
        val carrinho = carrinhoClient.buscar(request.carrinhoId)
        if (carrinho.itemIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível fechar um carrinho vazio")
        }
        val pedido = Pedido(
            carrinhoId = request.carrinhoId,
            endereco = request.endereco,
            observacao = request.observacao,
            itemIds = carrinho.itemIds.toMutableList()
        )
        return paraResponse(pedidoRepository.save(pedido))
    }

    @PatchMapping("/{id}/pagamento")
    fun atualizarStatusPagamento(
        @PathVariable id: Long,
        @RequestBody request: AtualizarPagamentoRequest
    ): PedidoResponse {
        val pedido = buscarEntidade(id)
        pedido.pago = request.pago
        pedido.status = if (request.pago) "Recebido" else "Aguardando pagamento"
        return paraResponse(pedidoRepository.save(pedido))
    }

    @PatchMapping("/{id}/status")
    fun atualizarStatus(
        @PathVariable id: Long,
        @RequestBody request: AtualizarStatusRequest
    ): PedidoResponse {
        if (request.status !in statusValidos) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Status inválido: ${request.status}")
        }
        val pedido = buscarEntidade(id)
        pedido.status = request.status
        pedido.pago = true
        return paraResponse(pedidoRepository.save(pedido))
    }

    private fun buscarEntidade(id: Long): Pedido =
        pedidoRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido $id não encontrado")
        }

    private fun paraResponse(p: Pedido) = PedidoResponse(
        p.id, p.carrinhoId, p.pago, p.status, p.endereco, p.observacao, p.itemIds.toList()
    )
}

data class CriarPedidoRequest(
    val carrinhoId: Long,
    val endereco: String? = null,
    val observacao: String? = null
)

data class AtualizarPagamentoRequest(val pago: Boolean)

data class AtualizarStatusRequest(val status: String)

data class PedidoResponse(
    val id: Long?,
    val carrinhoId: Long,
    val pago: Boolean,
    val status: String,
    val endereco: String?,
    val observacao: String?,
    val itemIds: List<Long>
)