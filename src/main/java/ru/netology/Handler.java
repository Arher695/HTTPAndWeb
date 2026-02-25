package ru.netology;

import java.io.BufferedOutputStream;
//handle(Request request, BufferedOutputStream responseStream):
//
//Функциональный интерфейс для обработки запросов
//Реализуется через лямбда-выражения при регистрации
@FunctionalInterface
public interface Handler {
    void handle(Request request, BufferedOutputStream responseStream);
}
