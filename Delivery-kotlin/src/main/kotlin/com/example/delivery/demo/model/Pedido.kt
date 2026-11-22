package com.example.cardapio.demo.model

import jakarta.persistence.*

@Entity
@Table(name = "pedido")
class Pedido(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "carrinho_id", nullable = false, unique = true)
    var carrinhoId: Long = 0L,

    @Column(nullable = false)
    var pago: Boolean = false,

    @Column(nullable = false)
    var status: String = "Aguardando pagamento",

    var endereco: String? = null,

    @Column(length = 500)
    var observacao: String? = null
)