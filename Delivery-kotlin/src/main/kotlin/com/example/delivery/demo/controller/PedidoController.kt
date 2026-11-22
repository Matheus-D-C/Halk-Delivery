package com.example.cardapio.demo.controller

import com.example.cardapio.demo.model.Pedido
import com.example.cardapio.demo.repository.CarrinhoRepository
import com.example.cardapio.demo.repository.PedidoRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/pedidos")
class PedidoController(
    private val pedidoRepository: PedidoRepository,
    private val carrinhoRepository: CarrinhoRepository
) {

    @GetMapping
    fun listarTodos(@RequestParam(required = false) pago: Boolean?): List<PedidoResponse> {
        val pedidos = if (pago != null) pedidoRepository.findByPago(pago) else pedidoRepository.findAll()
        return pedidos.sortedByDescending { it.id }.map { paraResponse(it) }
    }

    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long): PedidoResponse = paraResponse(buscarEntidade(id))

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

    private fun buscarEntidade(id: Long): Pedido =
        pedidoRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido $id não encontrado")
        }

    private fun paraResponse(p: Pedido): PedidoResponse {
        val itemIds = carrinhoRepository.findById(p.carrinhoId)
            .map { it.itemIds.toList() }
            .orElse(emptyList())
        return PedidoResponse(p.id, p.carrinhoId, p.pago, p.status, p.endereco, p.observacao, itemIds)
    }
}

data class AtualizarPagamentoRequest(val pago: Boolean)

data class PedidoResponse(
    val id: Long?,
    val carrinhoId: Long,
    val pago: Boolean,
    val status: String,
    val endereco: String?,
    val observacao: String?,
    val itemIds: List<Long>
)