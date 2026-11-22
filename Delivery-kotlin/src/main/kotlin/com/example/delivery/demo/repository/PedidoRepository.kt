package com.example.cardapio.demo.repository

import com.example.cardapio.demo.model.Pedido
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PedidoRepository : JpaRepository<Pedido, Long> {
    fun findByCarrinhoId(carrinhoId: Long): Pedido?
    fun existsByCarrinhoId(carrinhoId: Long): Boolean
    fun findByPago(pago: Boolean): List<Pedido>
}