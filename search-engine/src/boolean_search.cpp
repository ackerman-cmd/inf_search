#include <iostream>
#include <fstream>
#include <vector>
#include <algorithm>
#include <cstring>
#include <cctype>
#include <chrono>
#include <sstream>

using namespace std;
using namespace std::chrono;


class SimpleHashMap {
private:
    struct Node {
        string key;
        vector<int> values; 
        Node* next;
        Node(const string& k) : key(k), next(nullptr) {}
    };

    static const int TABLE_SIZE = 1000000;
    Node** table;

    unsigned int hashStr(const string& str) const {
        unsigned int h = 5381;
        for (size_t i = 0; i < str.size(); ++i) {
            h = ((h << 5) + h) + (unsigned char)str[i];
        }
        return h % TABLE_SIZE;
    }

public:
    SimpleHashMap() {
        table = new Node*[TABLE_SIZE]();
    }

    ~SimpleHashMap() {
        for (int i = 0; i < TABLE_SIZE; ++i) {
            Node* node = table[i];
            while (node) {
                Node* temp = node;
                node = node->next;
                delete temp;
            }
        }
        delete[] table;
    }

    void add(const string& key, int doc_id) {
        unsigned int h = hashStr(key);
        Node* node = table[h];

        while (node) {
            if (node->key == key) {
                if (node->values.empty() || node->values.back() != doc_id) {
                    node->values.push_back(doc_id);
                }
                return;
            }
            node = node->next;
        }

        Node* n = new Node(key);
        n->values.push_back(doc_id);
        n->next = table[h];
        table[h] = n;
    }

    vector<int> get(const string& key) const {
        unsigned int h = hashStr(key);
        Node* node = table[h];
        while (node) {
            if (node->key == key) return node->values;
            node = node->next;
        }
        return vector<int>();
    }
};


class BooleanSearch {
private:
    SimpleHashMap index;

   
    vector<string> doc_titles; 
    vector<string> doc_preview; 

    bool loadIndex(const string& filename) {
        ifstream file(filename.c_str());
        if (!file) {
            cerr << "Ошибка открытия файла индекса: " << filename << "\n";
            return false;
        }

        string line;

        if (!getline(file, line) || line != "DOCS") {
            cerr << "Bad index format: missing DOCS\n";
            return false;
        }

        if (!getline(file, line)) return false;
        int doc_count = atoi(line.c_str());
        doc_titles.assign(doc_count, "");
        doc_preview.assign(doc_count, "");

        for (int i = 0; i < doc_count; ++i) {
            if (!getline(file, line)) return false;
            size_t p1 = line.find('|');
            size_t p2 = line.find('|', p1 + 1);
            if (p1 == string::npos || p2 == string::npos) return false;

            int doc_id = atoi(line.substr(0, p1).c_str());
            string title = line.substr(p1 + 1, p2 - p1 - 1);
            string prev = line.substr(p2 + 1);

            if (doc_id >= 0 && doc_id < doc_count) {
                doc_titles[doc_id] = title;
                doc_preview[doc_id] = prev;
            }
        }

        if (!getline(file, line) || line != "TERMS") {
            cerr << "Bad index format: missing TERMS\n";
            return false;
        }

        if (!getline(file, line)) return false;
        int term_count = atoi(line.c_str());

        for (int i = 0; i < term_count; ++i) {
            if (!getline(file, line)) return false;
            size_t p = line.find('|');
            if (p == string::npos) continue;

            string term = line.substr(0, p);
            string doc_list_str = line.substr(p + 1);

            stringstream ss(doc_list_str);
            string tok;
            while (getline(ss, tok, ',')) {
                if (!tok.empty()) index.add(term, atoi(tok.c_str()));
            }
        }

        cout << "Индекс загружен успешно!\n";
        cout << "Документов: " << doc_titles.size() << "\n";
        cout << "Терминов (строк в файле): " << term_count << "\n";

        return true;
    }

    static vector<int> intersect(const vector<int>& a, const vector<int>& b) {
        vector<int> r;
        r.reserve(min(a.size(), b.size()));
        size_t i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            if (a[i] == b[j]) { r.push_back(a[i]); ++i; ++j; }
            else if (a[i] < b[j]) ++i;
            else ++j;
        }
        return r;
    }

    static vector<int> unionOp(const vector<int>& a, const vector<int>& b) {
        vector<int> r;
        r.reserve(a.size() + b.size());
        size_t i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            if (a[i] < b[j]) r.push_back(a[i++]);
            else if (a[i] > b[j]) r.push_back(b[j++]);
            else { r.push_back(a[i]); ++i; ++j; }
        }
        while (i < a.size()) r.push_back(a[i++]);
        while (j < b.size()) r.push_back(b[j++]);
        return r;
    }

    vector<int> notOp(const vector<int>& list) const {
        vector<int> r;
        r.reserve(doc_titles.size());

        size_t j = 0;
        for (int doc = 0; doc < (int)doc_titles.size(); ++doc) {
            while (j < list.size() && list[j] < doc) ++j;
            if (j < list.size() && list[j] == doc) continue;
            r.push_back(doc);
        }
        return r;
    }

    static vector<string> tokenizeQuery(const string& query) {
        vector<string> tokens;
        stringstream ss(query);
        string t;

        while (ss >> t) {
            for (size_t i = 0; i < t.size(); ++i) {
                t[i] = (char)tolower((unsigned char)t[i]);
            }

            while (!t.empty() && !isalnum((unsigned char)t.back())) t.pop_back();
            while (!t.empty() && !isalnum((unsigned char)t.front())) t.erase(t.begin());

            if (!t.empty()) tokens.push_back(t);
        }
        return tokens;
    }

public:
    bool init(const string& index_file) { return loadIndex(index_file); }

   
    vector<int> executeQuery(const string& query) {
        vector<string> tokens = tokenizeQuery(query);
        if (tokens.empty()) return vector<int>();

        if (tokens.size() == 1) {
            return index.get(tokens[0]);
        }

        if (tokens.size() == 2 && tokens[0] == "not") {
            return notOp(index.get(tokens[1]));
        }

        if (tokens.size() == 3) {
            vector<int> a = index.get(tokens[0]);
            vector<int> b = index.get(tokens[2]);
            if (tokens[1] == "and") return intersect(a, b);
            if (tokens[1] == "or")  return unionOp(a, b);
        }

        vector<int> result;
        bool first = true;
        for (size_t i = 0; i < tokens.size(); ++i) {
            const string& t = tokens[i];
            if (t == "and" || t == "or" || t == "not") continue;

            vector<int> cur = index.get(t);
            if (first) { result = cur; first = false; }
            else { result = intersect(result, cur); }
        }
        return result;
    }

    void printResults(const vector<int>& results, int limit = 10) const {
        if (results.empty()) {
            cout << "Не найдено документов.\n";
            return;
        }

        cout << "==========================================\n";
        cout << "Найдено документов: " << results.size() << "\n";
        cout << "Показано первых " << min(limit, (int)results.size()) << ":\n";
        cout << "==========================================\n";

        int shown = min(limit, (int)results.size());
        for (int i = 0; i < shown; ++i) {
            int doc_id = results[i];
            string title = (doc_id >= 0 && doc_id < (int)doc_titles.size()) ? doc_titles[doc_id] : "";
            string preview = (doc_id >= 0 && doc_id < (int)doc_preview.size()) ? doc_preview[doc_id] : "";

            cout << "[" << (i + 1) << "] internal_id: " << doc_id << "\n";
            cout << "    external_id: " << title << "\n";
            if (!preview.empty()) cout << "    preview: " << preview << "\n";
            cout << "------------------------------------------\n";
        }

        if ((int)results.size() > limit) {
            cout << "... и еще " << ((int)results.size() - limit) << " документов\n";
        }
    }

    void interactiveSearch() {
        cout << "\n=== БУЛЕВ ПОИСК ===\n";
        cout << "Поддерживаемые операции:\n";
        cout << "  - word1 word2 (AND по умолчанию)\n";
        cout << "  - word1 AND word2\n";
        cout << "  - word1 OR word2\n";
        cout << "  - NOT word\n";
        cout << "Введите 'quit' для выхода\n";

        string query;
        while (true) {
            cout << "\n>> ";
            getline(cin, query);
            if (query == "quit" || query == "exit" || query == "q") break;
            if (query.empty()) continue;

            auto start = high_resolution_clock::now();
            vector<int> results = executeQuery(query);
            auto end = high_resolution_clock::now();

            printResults(results, 5);
            cout << "Время поиска: " << duration_cast<milliseconds>(end - start).count() << " мс\n";
        }
    }
};

int main(int argc, char* argv[]) {
    BooleanSearch searcher;
    string index_file = "data/boolean_index.idx";

    if (argc >= 2) {
        if (string(argv[1]) == "--index" && argc >= 3) {
            index_file = argv[2];
        }
    }

    ifstream index_check(index_file.c_str());
    if (!index_check) {
        cerr << "Индекс не найден: " << index_file << "\n";
        cerr << "Сначала постройте индекс вашим index_builder.\n";
        return 1;
    }

    if (!searcher.init(index_file)) {
        cerr << "Ошибка загрузки индекса!\n";
        return 1;
    }

    if (argc == 2 && string(argv[1]).rfind("--", 0) != 0) {
        string query = argv[1];
        vector<int> results = searcher.executeQuery(query);
        searcher.printResults(results, 5);
        return 0;
    }

    searcher.interactiveSearch();
    return 0;
}
