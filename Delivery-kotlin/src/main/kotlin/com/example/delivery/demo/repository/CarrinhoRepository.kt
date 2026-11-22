package com.example.cardapio.demo.repository

import com.example.cardapio.demo.model.Carrinho
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CarrinhoRepository : JpaRepository<Carrinho, Long> {
    fun findByClienteId(clienteId: Long): List<Carrinho>
}