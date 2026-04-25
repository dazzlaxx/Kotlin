package org.example

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

//Data class для десериализации JSON ответа от GitHub API
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRepo(
    val name: String,
    val stargazers_count: Int,
    val forks_count: Int,
    val language: String?,
    val updated_at: String
)

fun main() {
    //Создаем HTTP клиент
    val client = OkHttpClient()

    //Запрашиваем имя пользователя
    println("Введите имя пользователя GitHub:")
    val username = readln().trim()

    if (username.isEmpty()) {
        println("Ошибка: имя пользователя не может быть пустым")
        return
    }

    println("Получение данных для пользователя: $username...")

    try {
        //Формируем URL для запроса к GitHub API
        val url = "https://api.github.com/users/$username/repos"

        //Создаем HTTP запрос с заголовками
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "GitHub-Report-Generator")
            .build()

        //Выполняем запрос
        val response = client.newCall(request).execute()

        //Проверяем успешность запроса
        if (!response.isSuccessful) {
            when (response.code) {
                404 -> {
                    println("Ошибка: пользователь '$username' не найден")
                    return
                }
                403 -> {
                    println("Ошибка: превышен лимит запросов к API GitHub. Попробуйте позже.")
                    return
                }
                else -> {
                    println("Ошибка при получении данных: ${response.code} ${response.message}")
                    return
                }
            }
        }

        //Получаем тело ответа как строку
        val responseBody = response.body?.string()
        response.close()

        if (responseBody.isNullOrEmpty()) {
            println("Ошибка: получен пустой ответ от сервера")
            return
        }

        //Парсим JSON в список объектов GitHubRepo
        val mapper = jacksonObjectMapper()
        val repos: List<GitHubRepo> = mapper.readValue(responseBody)

        if (repos.isEmpty()) {
            println("У пользователя '$username' нет публичных репозиториев")
            return
        }

        println("Найдено репозиториев: ${repos.size}")

        //Рассчитываем агрегированные показатели
        val totalStars = repos.sumOf { it.stargazers_count }
        val totalForks = repos.sumOf { it.forks_count }
        val averageStars = if (repos.isNotEmpty()) totalStars.toDouble() / repos.size else 0.0
        val mostPopularRepo = repos.maxByOrNull { it.stargazers_count }
        val languages = repos.mapNotNull { it.language }.distinct()

        //Выводим статистику в консоль
        println("Общее количество репозиториев: ${repos.size}")
        println("Общее количество звезд: $totalStars")
        println("Общее количество форков: $totalForks")
        println("Среднее количество звезд: ${"%.2f".format(averageStars)}")

        if (mostPopularRepo != null) {
            println("Самый популярный репозиторий: ${mostPopularRepo.name} (${mostPopularRepo.stargazers_count} звезд)")
        }

        println("Используемые языки: ${languages.ifEmpty { listOf("не указаны") }.joinToString(", ")}")

        //Генерируем CSV файл
        val csvFileName = "${username}_github_report.csv"
        generateCsvReport(repos, csvFileName)

        println("\nОтчет сохранен в файл: $csvFileName")

    } catch (e: IOException) {
        println("Ошибка сетевого соединения: ${e.message}")
    } catch (e: Exception) {
        println("Непредвиденная ошибка: ${e.message}")
        e.printStackTrace()
    }
}

fun generateCsvReport(repos: List<GitHubRepo>, filename: String) {
    try {
        val file = File(filename)

        //Используем StringBuilder для формирования CSV
        val csvContent = StringBuilder()

        //Добавляем BOM для корректного отображения в Excel
        csvContent.append("\uFEFF")

        csvContent.appendLine("Название репозитория,Количество звезд,Количество форков,Язык программирования,Дата последнего обновления")

        //Данные по каждому репозиторию
        for (repo in repos) {
            val language = repo.language ?: "Не указан"
            //Форматируем дату (оставляем только дату, без времени)
            val formattedDate = if (repo.updated_at.length >= 10) {
                repo.updated_at.substring(0, 10)
            } else {
                repo.updated_at
            }

            //Экранируем запятые в названии
            val name = if (repo.name.contains(",")) "\"${repo.name}\"" else repo.name

            csvContent.appendLine("$name,${repo.stargazers_count},${repo.forks_count},$language,$formattedDate")
        }

        //Добавляем итоговую строку
        csvContent.appendLine("")
        csvContent.appendLine("ИТОГО,Среднее: ${"%.2f".format(repos.sumOf { it.stargazers_count }.toDouble() / repos.size)}," +
                "Всего форков: ${repos.sumOf { it.forks_count }}," +
                "Всего языков: ${repos.mapNotNull { it.language }.distinct().size},")

        //Записываем в файл
        file.writeText(csvContent.toString(), Charsets.UTF_8)

    } catch (e: Exception) {
        println("Ошибка при создании CSV файла: ${e.message}")
        throw e
    }
}