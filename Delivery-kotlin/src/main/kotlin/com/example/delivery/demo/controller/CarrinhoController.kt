package com.example.cardapio.demo.controller

import com.example.cardapio.demo.model.Carrinho
import com.example.cardapio.demo.model.Pedido
import com.example.cardapio.demo.repository.CarrinhoRepository
import com.example.cardapio.demo.repository.PedidoRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/carrinhos")
class CarrinhoController(
    private val carrinhoRepository: CarrinhoRepository,
    private val pedidoRepository: PedidoRepository
) {

    @GetMapping
    fun listarTodos(@RequestParam(required = false) clienteId: Long?): List<Carrinho> =
        if (clienteId != null) {
            carrinhoRepository.findByClienteId(clienteId)
        } else {
            carrinhoRepository.findAll()
        }

    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long): Carrinho =
        carrinhoRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Carrinho $id não encontrado")
        }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(@RequestBody request: CarrinhoRequest): Carrinho {
        val carrinho = Carrinho(
            clienteId = request.clienteId,
            itemIds = request.itemIds.toMutableList()
        )
        return carrinhoRepository.save(carrinho)
    }
    
    @PutMapping("/{id}")
    fun atualizar(@PathVariable id: Long, @RequestBody request: CarrinhoRequest): Carrinho {
    val carrinho = buscarPorId(id)
    carrinho.clienteId = request.clienteId
    carrinho.itemIds = request.itemIds.toMutableList()
    return carrinhoRepository.save(carrinho)
    }

    @PostMapping("/{id}/fechar")
    @ResponseStatus(HttpStatus.CREATED)
    fun fecharCarrinho(
    @PathVariable id: Long,
    @RequestBody(required = false) request: FecharCarrinhoRequest?
    ): Pedido {
    val carrinho = buscarPorId(id)
    val carrinhoId = carrinho.id
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Carrinho sem ID válido")

    if (carrinho.itemIds.isEmpty()) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível fechar um carrinho vazio")
    }
    if (pedidoRepository.existsByCarrinhoId(carrinhoId)) {
        throw ResponseStatusException(HttpStatus.CONFLICT, "Já existe um pedido criado para o carrinho $carrinhoId")
    }

    return pedidoRepository.save(
        Pedido(carrinhoId = carrinhoId, endereco = request?.endereco, observacao = request?.observacao)
    )
    }

    data class FecharCarrinhoRequest(val endereco: String? = null, val observacao: String? = null)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletar(@PathVariable id: Long) {
        if (!carrinhoRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Carrinho $id não encontrado")
        }
        carrinhoRepository.deleteById(id)
    }
}

data class CarrinhoRequest(
    val clienteId: Long,
    val itemIds: List<Long> = emptyList()
)