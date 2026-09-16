package com.reus.nutri

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

@Serializable
data class TodoItem(val id: Int, val name: String)

val supabase = createSupabaseClient(
    supabaseUrl = "https://wwcemcbyesjqoshksnfv.supabase.co",
    supabaseKey = "sb_publishable_g_fMcwqXouPxY5MASw0Etg_uFTvUvdR",
) {
    install(Postgrest)
}

suspend fun loadTodos(): List<TodoItem> = supabase.from("todos").select().decodeList()
