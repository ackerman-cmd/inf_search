#include <iostream>
#include <string>
#include <vector>
#include <algorithm>
#include <cstring>
#include <cctype>
#include <chrono>
#include <cmath>

using namespace std;
using namespace std::chrono;

struct HashNode {
    char* word;
    int frequency;
    HashNode* next;
};

class FrequencyTable {
    static const int TABLE_SIZE = 1000000;
    HashNode** table;
    long long total_tokens;
    long long total_chars;
    long long unique_words;

    unsigned int hash(const char* str) {
        unsigned int h = 5381;
        int c;
        while ((c = *str++)) {
            h = ((h << 5) + h) + c;
        }
        return h % TABLE_SIZE;
    }

public:
    FrequencyTable() : total_tokens(0), total_chars(0), unique_words(0) {
        table = new HashNode*[TABLE_SIZE]();
    }

    ~FrequencyTable() {
        for (int i = 0; i < TABLE_SIZE; ++i) {
            HashNode* node = table[i];
            while (node) {
                HashNode* temp = node;
                node = node->next;
                free(temp->word);
                delete temp;
            }
        }
        delete[] table;
    }

    void add(const string& w) {
        total_tokens++;
        total_chars += w.length();
        
        unsigned int h = hash(w.c_str());
        HashNode* node = table[h];
        
        while (node) {
            if (strcmp(node->word, w.c_str()) == 0) {
                node->frequency++;
                return;
            }
            node = node->next;
        }

        unique_words++;
        HashNode* newNode = new HashNode;
        newNode->word = strdup(w.c_str());
        newNode->frequency = 1;
        newNode->next = table[h];
        table[h] = newNode;
    }

    long long getTotalTokens() const { return total_tokens; }
    long long getTotalChars() const { return total_chars; }
    long long getUniqueWords() const { return unique_words; }
    double getAvgTokenLength() const { 
        return total_tokens > 0 ? (double)total_chars / total_tokens : 0.0; 
    }

    struct Entry {
        string word;
        int freq;
    };

    vector<Entry> getAllEntries() {
        vector<Entry> entries;
        for (int i = 0; i < TABLE_SIZE; ++i) {
            HashNode* node = table[i];
            while (node) {
                entries.push_back({node->word, node->frequency});
                node = node->next;
            }
        }
        return entries;
    }
};

int main(int argc, char* argv[]) {
    string input_file = "data/corpus.txt";
    string output_file = "results/frequencies.csv";
    
    if (argc > 1) input_file = argv[1];
    if (argc > 2) output_file = argv[2];
    
    freopen(input_file.c_str(), "r", stdin);
    freopen(output_file.c_str(), "w", stdout);
    freopen("results/stats.txt", "w", stderr);
    
    auto start_time = high_resolution_clock::now();
    FrequencyTable ft;
    string current;
    long long input_bytes = 0;
    
    char c;
    while (cin.get(c)) {
        input_bytes++;
        if (isalnum(static_cast<unsigned char>(c))) {
            current += static_cast<char>(tolower(static_cast<unsigned char>(c)));
        } else {
            if (current.length() >= 2) {
                ft.add(current);
            }
            current.clear();
        }
    }
    
    if (current.length() >= 2) {
        ft.add(current);
    }
    
    auto end_time = high_resolution_clock::now();
    auto duration = duration_cast<milliseconds>(end_time - start_time);
    
    cerr << "======= СТАТИСТИКА ТОКЕНИЗАЦИИ =======" << endl;
    cerr << "Общий объем данных: " << input_bytes << " байт (" 
         << input_bytes/1024 << " KB)" << endl;
    cerr << "Всего токенов: " << ft.getTotalTokens() << endl;
    cerr << "Уникальных слов: " << ft.getUniqueWords() << endl;
    cerr << "Средняя длина токена: " << ft.getAvgTokenLength() << " символов" << endl;
    cerr << "Время выполнения: " << duration.count() << " мс" << endl;
    cerr << "Скорость обработки: " 
         << (input_bytes > 0 ? (double)input_bytes / duration.count() : 0) 
         << " байт/мс" << endl;
    cerr << "Скорость обработки: " 
         << (input_bytes > 0 ? (double)input_bytes / duration.count() / 1024 : 0) 
         << " KB/мс" << endl;
    cerr << "Скорость токенизации: " 
         << (ft.getTotalTokens() > 0 ? (double)ft.getTotalTokens() / duration.count() * 1000 : 0) 
         << " токенов/сек" << endl;
    cerr << "======================================" << endl;
    
    auto entries = ft.getAllEntries();
    sort(entries.begin(), entries.end(), [](const FrequencyTable::Entry& a, const FrequencyTable::Entry& b) {
        return a.freq > b.freq;
    });
    
    cout << "Rank,Frequency,Word" << endl;
    for (size_t i = 0; i < entries.size(); ++i) {
        cout << i + 1 << "," << entries[i].freq << "," << entries[i].word << endl;
    }
    
    return 0;
}