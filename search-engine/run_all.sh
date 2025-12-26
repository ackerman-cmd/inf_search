cat > run_all.sh << 'EOF'
#!/bin/bash

echo "🚀 ПОЛНЫЙ ЗАПУСК ПОИСКОВОЙ СИСТЕМЫ"
echo "================================"

# 1. Проверяем наличие корпуса
if [ ! -f "data/corpus.txt" ]; then
    echo "❌ Файл корпуса не найден: data/corpus.txt"
    echo "Создаем тестовый корпус..."
    
    cat > data/corpus.txt << 'CORPUS'
Artificial Intelligence|Artificial intelligence (AI) is intelligence demonstrated by machines. AI research has been defined as the field of study of intelligent agents.
Machine Learning|Machine learning (ML) is a subset of artificial intelligence that allows computers to learn without being explicitly programmed.
Natural Language Processing|Natural language processing (NLP) is a subfield of AI that focuses on the interaction between computers and human language.
Computer Vision|Computer vision is an interdisciplinary field that deals with how computers can gain understanding from digital images or videos.
Robotics|Robotics is an interdisciplinary branch of computer science and engineering that involves the design, construction, operation of robots.
Data Science|Data science is an interdisciplinary field that uses scientific methods to extract knowledge and insights from structured and unstructured data.
Neural Networks|Neural networks are computing systems inspired by biological neural networks that constitute animal brains.
Deep Learning|Deep learning is part of a broader family of machine learning methods based on artificial neural networks.
Algorithm|An algorithm is a finite sequence of well-defined instructions, typically used to solve a class of specific problems or to perform a computation.
Programming|Computer programming is the process of designing and building an executable computer program to accomplish a specific computing result.
Database|A database is an organized collection of data, generally stored and accessed electronically from a computer system.
Network|A computer network is a set of computers sharing resources located on or provided by network nodes.
Security|Computer security, cybersecurity or information technology security is the protection of computer systems and networks from information disclosure.
Software Engineering|Software engineering is the systematic application of engineering approaches to the development of software.
Operating System|An operating system is system software that manages computer hardware, software resources, and provides common services for computer programs.
Compiler|A compiler is a computer program that translates computer code written in one programming language into another language.
Cryptography|Cryptography is the practice and study of techniques for secure communication in the presence of adversarial behavior.
Big Data|Big data is a field that treats ways to analyze, systematically extract information from, or otherwise deal with data sets that are too large or complex.
Internet of Things|The Internet of things describes the network of physical objects that are embedded with sensors, software, and other technologies.
Cloud Computing|Cloud computing is the on-demand availability of computer system resources, especially data storage and computing power.
CORPUS
    
    echo "✅ Создан тестовый корпус из 20 документов"
fi

# 2. Компилируем
echo ""
echo "🔧 Компиляция программ..."
./compile.sh

# 3. Токенизация
echo ""
echo "📊 1. ТОКЕНИЗАЦИЯ"
echo "Запуск токенизатора..."
./bin/tokenizer data/corpus.txt results/frequencies.csv 2> results/stats.txt
echo "Результаты:"
cat results/stats.txt

# 4. Анализ Ципфа
echo ""
echo "📈 2. ЗАКОН ЦИПФА"
echo "Анализ распределения частот..."
python3 src/zipf_analyzer.py results/frequencies.csv

# 5. Построение индекса
echo ""
echo "🗂 3. ПОСТРОЕНИЕ БУЛЕВА ИНДЕКСА"
echo "Создание инвертированного индекса..."
./bin/index_builder data/corpus.txt data/boolean_index.idx


# 7. Запуск интерактивного поиска
echo ""
echo "💻 5. ИНТЕРАКТИВНЫЙ ПОИСК"
echo "Запуск поисковой системы..."
echo ""
./bin/search

chmod +x run_all.sh