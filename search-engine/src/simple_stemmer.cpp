#include <iostream>
#include <string>
#include <cstring>
#include <cctype>
#include <fstream>
#include <vector>

using namespace std;

class SimpleStemmer {
public:
    string stem(const string& word) {
        string result = word;
        
        // Простая реализация - удаление окончаний
        string suffixes[] = {"ing", "ed", "ly", "es", "s", "'s"};
        
        for (const auto& suffix : suffixes) {
            if (result.length() > suffix.length() + 2 && endsWith(result, suffix)) {
                result = result.substr(0, result.length() - suffix.length());
                break;
            }
        }
        
        // Дополнительные правила
        if (endsWith(result, "ies") && result.length() > 5) {
            result = result.substr(0, result.length() - 3) + "y";
        } else if (endsWith(result, "ied") && result.length() > 5) {
            result = result.substr(0, result.length() - 3) + "y";
        } else if (endsWith(result, "iness") && result.length() > 6) {
            result = result.substr(0, result.length() - 5) + "y";
        } else if (endsWith(result, "ization") && result.length() > 8) {
            result = result.substr(0, result.length() - 7) + "ize";
        } else if (endsWith(result, "ational") && result.length() > 8) {
            result = result.substr(0, result.length() - 7) + "ate";
        } else if (endsWith(result, "tional") && result.length() > 7) {
            result = result.substr(0, result.length() - 6) + "tion";
        } else if (endsWith(result, "biliti") && result.length() > 7) {
            result = result.substr(0, result.length() - 6) + "ble";
        } else if (endsWith(result, "fulness") && result.length() > 8) {
            result = result.substr(0, result.length() - 7) + "ful";
        } else if (endsWith(result, "ousness") && result.length() > 8) {
            result = result.substr(0, result.length() - 7) + "ous";
        }
        
        // Удаление двойных согласных
        if (result.length() > 3 && result[result.length()-1] == result[result.length()-2]) {
            char last = tolower(result[result.length()-1]);
            if (!isVowel(last)) {
                result = result.substr(0, result.length() - 1);
            }
        }
        
        return result;
    }

private:
    bool endsWith(const string& word, const string& suffix) {
        if (word.length() < suffix.length()) return false;
        return word.substr(word.length() - suffix.length()) == suffix;
    }
    
    bool isVowel(char c) {
        c = tolower(c);
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }
};

void testStemmer() {
    SimpleStemmer stemmer;
    
    vector<string> test_words = {
        "running", "runner", "runs", "ran", "happily", 
        "happiness", "happier", "cats", "computing", 
        "computer", "computation", "jumping", "jumped"
    };
    
    cout << "Тестирование стеммера:" << endl;
    cout << "======================" << endl;
    
    for (const auto& word : test_words) {
        cout << word << " -> " << stemmer.stem(word) << endl;
    }
}

void processFile(const string& input_file, const string& output_file) {
    SimpleStemmer stemmer;
    ifstream in(input_file);
    ofstream out(output_file);
    
    string word;
    while (in >> word) {
        // Очистка слова от знаков препинания
        string clean_word;
        for (char c : word) {
            if (isalnum(c)) clean_word += tolower(c);
        }
        
        if (clean_word.length() >= 2) {
            out << stemmer.stem(clean_word) << " ";
        }
    }
    
    cout << "Стемминг завершен. Результат в " << output_file << endl;
}

int main(int argc, char* argv[]) {
    if (argc == 1) {
        testStemmer();
    } else if (argc == 3) {
        processFile(argv[1], argv[2]);
    } else {
        cout << "Использование:" << endl;
        cout << "  " << argv[0] << "                   # тестирование" << endl;
        cout << "  " << argv[0] << " input.txt output.txt # обработка файла" << endl;
    }
    
    return 0;
}
