#!/bin/bash

echo "Компиляция поисковой системы..."
echo "================================="


echo "1. Компиляция токенизатора..."
g++ -std=c++11 -O2 src/tokenizer.cpp -o bin/tokenizer
if [ $? -eq 0 ]; then
    echo "Успешно"
else
    echo "Ошибка"
    exit 1
fi

echo "2. Компиляция стеммера..."
g++ -std=c++11 -O2 src/simple_stemmer.cpp -o bin/stemmer
if [ $? -eq 0 ]; then
    echo "Успешно"
else
    echo "Ошибка"
    exit 1
fi

echo "3. Компиляция построителя булева индекса..."
g++ -std=c++11 -O2 src/boolean_index.cpp -o bin/index_builder
if [ $? -eq 0 ]; then
    echo "Успешно"
else
    echo "Ошибка"
    exit 1
fi

echo "4. Компиляция булева поиска..."
g++ -std=c++11 -O2 src/boolean_search.cpp -o bin/search
if [ $? -eq 0 ]; then
    echo "Успешно"
else
    echo "Ошибка"
    exit 1
fi

chmod +x compile.sh