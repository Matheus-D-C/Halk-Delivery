package com.example.cardapio.demo.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException

@Component
class CarrinhoClient(@Value("\${carrinho.url:http://localhost:8002}") baseUrl: String) {

    private val urlNormalizada = if (baseUrl.startsWith("http")) baseUrl else "http://$baseUrl"
    private val http = RestClient.create(urlNormalizada)

    fun buscar(id: Long): CarrinhoDto =
        try {
            http.get().uri("/api/carrinhos/{id}", id).retrieve().body(CarrinhoDto::class.java)
                ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta vazia do serviço de carrinho")
        } catch (e: HttpClientErrorException.NotFound) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Carrinho $id não encontrado")
        } catch (e: ResourceAccessException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de carrinho indisponível")
        }
}

data class CarrinhoDto(
    val id: Long? = null,
    val clienteId: Long = 0L,
    val itemIds: List<Long> = emptyList()
)