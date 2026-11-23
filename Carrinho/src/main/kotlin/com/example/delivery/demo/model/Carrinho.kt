package com.example.cardapio.demo.model

import jakarta.persistence.*

@Entity
@Table(name = "carrinho")
class Carrinho (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(name= "cliente_id", nullable = false)
    var clienteId: Long = 0L,
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "carrinho_itens_ids", joinColumns = [JoinColumn(name = "carrinho_id")])
    @Column(name = "item_id", nullable = false)
    var itemIds: MutableList<Long> = mutableListOf()    
)