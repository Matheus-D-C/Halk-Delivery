package com.example.cardapio.demo.controller

import com.example.cardapio.demo.model.Item
import com.example.cardapio.demo.repository.ItemRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/itens")
class ItemController(
    private val itemRepository: ItemRepository
) {

    @GetMapping
    fun listarTodos(
        @RequestParam(required = false) completed: Boolean?,
        @RequestParam(required = false) nome: String?
    ): List<Item> = when {
        completed != null -> itemRepository.findByCompleted(completed)
        !nome.isNullOrBlank() -> itemRepository.findByNomeContainingIgnoreCase(nome)
        else -> itemRepository.findAll()
    }

    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long): Item =
        itemRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Item $id não encontrado")
        }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(@RequestBody request: ItemRequest): Item {
        val nomeLimpo = request.nome.trim()
        if (nomeLimpo.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "O nome do item não pode ser vazio")
        }

        val item = Item(
            nome = nomeLimpo,
            completed = request.completed ?: false
        )
        return itemRepository.save(item)
    }

    @PutMapping("/{id}")
    fun atualizar(@PathVariable id: Long, @RequestBody request: ItemRequest): Item {
        val item = buscarPorId(id)
        val nomeLimpo = request.nome.trim()
        if (nomeLimpo.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "O nome do item não pode ser vazio")
        }

        item.nome = nomeLimpo
        if (request.completed != null) {
            item.completed = request.completed
        }
        return itemRepository.save(item)
    }

    @PatchMapping("/{id}/toggle")
    fun alternarCompleted(@PathVariable id: Long): Item {
        val item = buscarPorId(id)
        item.completed = !item.completed
        return itemRepository.save(item)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletar(@PathVariable id: Long) {
        if (!itemRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item $id não encontrado")
        }
        itemRepository.deleteById(id)
    }
}

data class ItemRequest(
    val nome: String,
    val completed: Boolean? = null
)