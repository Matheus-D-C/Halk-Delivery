package com.example.cardapio.demo.controller

import com.example.cardapio.demo.model.Carrinho
import com.example.cardapio.demo.repository.CarrinhoRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/carrinhos")
class CarrinhoController(private val carrinhoRepository: CarrinhoRepository) {

    @GetMapping
    fun listarTodos(@RequestParam(required = false) clienteId: Long?): List<Carrinho> =
        if (clienteId != null) carrinhoRepository.findByClienteId(clienteId)
        else carrinhoRepository.findAll()

    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long): Carrinho =
        carrinhoRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Carrinho $id não encontrado")
        }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(@RequestBody request: CarrinhoRequest): Carrinho =
        carrinhoRepository.save(
            Carrinho(clienteId = request.clienteId, itemIds = request.itemIds.toMutableList())
        )

    @PutMapping("/{id}")
    fun atualizar(@PathVariable id: Long, @RequestBody request: CarrinhoRequest): Carrinho {
        val carrinho = buscarPorId(id)
        carrinho.clienteId = request.clienteId
        carrinho.itemIds = request.itemIds.toMutableList()
        return carrinhoRepository.save(carrinho)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletar(@PathVariable id: Long) {
        if (!carrinhoRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Carrinho $id não encontrado")
        }
        carrinhoRepository.deleteById(id)
    }
}

data class CarrinhoRequest(val clienteId: Long, val itemIds: List<Long> = emptyList())