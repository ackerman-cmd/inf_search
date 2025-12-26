#!/bin/bash

echo "ПОЛНЫЙ ЗАПУСК ПОИСКОВОЙ СИСТЕМЫ"
echo "================================"
echo ""
echo "Компиляция программ..."
./compile.sh

echo ""
echo "1. ТОКЕНИЗАЦИЯ"
echo "Запуск токенизатора..."
./bin/tokenizer data/corpus.txt results/frequencies.csv > results/stats.txt
echo "Результаты:"
cat results/stats.txt

echo ""
echo "2. ЗАКОН ЦИПФА"
echo "Анализ распределения частот..."
python3 src/zipf_analyzer.py results/frequencies.csv

echo ""
echo "3. ПОСТРОЕНИЕ БУЛЕВА ИНДЕКСА"
echo "Создание инвертированного индекса..."
./bin/index_builder data/corpus.txt data/boolean_index.idx


echo ""
echo "5. ИНТЕРАКТИВНЫЙ ПОИСК"
echo "Запуск поисковой системы..."
echo ""
./bin/search

chmod +x run_all.sh