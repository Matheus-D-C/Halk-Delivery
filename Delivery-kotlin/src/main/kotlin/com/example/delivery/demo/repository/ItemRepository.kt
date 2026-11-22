package com.example.cardapio.demo.repository

import com.example.cardapio.demo.model.Item
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ItemRepository : JpaRepository<Item, Long> {
    fun findByCompleted(completed: Boolean): List<Item>
    fun findByNomeContainingIgnoreCase(nome: String): List<Item>
}