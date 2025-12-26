#include <iostream>
#include <fstream>
#include <vector>
#include <algorithm>
#include <cstring>
#include <cctype>
#include <chrono>
#include <sstream>

using namespace std;

static inline void ltrim(string& s) {
    size_t i = 0;
    while (i < s.size() && (s[i] == ' ' || s[i] == '\t')) i++;
    if (i) s.erase(0, i);
}

static inline void rtrim(string& s) {
    while (!s.empty() && (s.back() == ' ' || s.back() == '\t' || s.back() == '\r' || s.back() == '\n')) {
        s.pop_back();
    }
}

static inline void trim(string& s) {
    if (s.size() >= 3 &&
        (unsigned char)s[0] == 0xEF &&
        (unsigned char)s[1] == 0xBB &&
        (unsigned char)s[2] == 0xBF) {
        s.erase(0, 3);
    }
    rtrim(s);
    ltrim(s);
}

static inline bool safeGetline(ifstream& f, string& s) {
    if (!getline(f, s)) return false;
    trim(s);
    return true;
}

static inline string sanitize(string s) {
    for (char& c : s) {
        if (c == '|' || c == '\r' || c == '\n') c = ' ';
    }
    return s;
}

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
        for (unsigned char c : str) {
            h = ((h << 5) + h) + c;
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
                Node* tmp = node;
                node = node->next;
                delete tmp;
            }
        }
        delete[] table;
    }

    void add(const string& key, int doc_id) {
        unsigned int h = hashStr(key);
        Node* node = table[h];
        while (node) {
            if (node->key == key) {
                if (node->values.empty() || node->values.back() != doc_id)
                    node->values.push_back(doc_id);
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
        return {};
    }

    vector<pair<string, vector<int>>> getAll() const {
        vector<pair<string, vector<int>>> r;
        for (int i = 0; i < TABLE_SIZE; ++i) {
            Node* node = table[i];
            while (node) {
                r.push_back({node->key, node->values});
                node = node->next;
            }
        }
        return r;
    }
};

class BooleanIndex {
private:
    SimpleHashMap index;
    vector<string> titles;
    vector<string> previews;

    vector<string> tokenize_unique(const string& text) {
        vector<string> t;
        string cur;
        for (unsigned char c : text) {
            if (isalnum(c)) cur.push_back((char)tolower(c));
            else {
                if (cur.size() >= 2) t.push_back(cur);
                cur.clear();
            }
        }
        if (cur.size() >= 2) t.push_back(cur);
        sort(t.begin(), t.end());
        t.erase(unique(t.begin(), t.end()), t.end());
        return t;
    }

public:
    void addDocument(int id, const string& title, const string& content) {
        if ((int)titles.size() <= id) titles.resize(id + 1);
        if ((int)previews.size() <= id) previews.resize(id + 1);
        
        string clean_preview;
        if (content.size() > 200) {
            clean_preview = content.substr(0, 200);
        } else {
            clean_preview = content;
        }
        
        for (char& c : clean_preview) {
            if (c == '\n' || c == '\r') c = ' ';
        }
        
        titles[id] = title;
        previews[id] = clean_preview;
        
        auto toks = tokenize_unique(content);
        for (auto& tok : toks) {
            index.add(tok, id);
        }
    }

    bool saveToFile(const string& file) const {
        ofstream f(file);
        if (!f) {
            cerr << "Не удалось открыть файл для записи: " << file << endl;
            return false;
        }
        
        f << "DOCS\n";
        f << titles.size() << "\n";
        
        for (size_t i = 0; i < titles.size(); ++i) {
            string clean_title = sanitize(titles[i]);
            string clean_preview = sanitize(previews[i]);
            f << i << "|" << clean_title << "|" << clean_preview << "\n";
        }
        
        f << "TERMS\n";
        auto all = index.getAll();
        f << all.size() << "\n";
        
        for (auto& e : all) {
            f << e.first << "|";
            for (size_t i = 0; i < e.second.size(); ++i) {
                if (i) f << ",";
                f << e.second[i];
            }
            f << "\n";
        }
        
        f.close();
        return true;
    }
};

static bool buildIndex(const string& dump, const string& out) {
    ifstream f(dump);
    if (!f) {
        cerr << "Не удалось открыть файл дампа: " << dump << endl;
        return false;
    }

    BooleanIndex idx;
    string line, ext, content;
    bool inDoc = false, inContent = false;
    int id = 0;

    while (safeGetline(f, line)) {
        if (line.empty()) continue;

        if (line == "==DOC_START==") {
            inDoc = true;
            inContent = false;
            ext.clear();
            content.clear();
            continue;
        }
        if (!inDoc) continue;

        if (ext.empty()) {
            ext = line;
            continue;
        }
        if (!inContent) {
            if (line == "==CONTENT_START==") {
                inContent = true;
            }
            continue;
        }
        if (line == "==DOC_END==") {
            if (!ext.empty() && !content.empty()) {
                idx.addDocument(id++, ext, content);
            }
            inDoc = false;
            inContent = false;
            continue;
        }
        content += line + " ";
    }
    
    if (inDoc && !ext.empty() && !content.empty()) {
        idx.addDocument(id++, ext, content);
    }
    
    cout << "Обработано документов: " << id << endl;
    return idx.saveToFile(out);
}

int main(int argc, char* argv[]) {
    if (argc != 3) {
        cerr << "Использование: " << argv[0] << " <входной_файл> <выходной_файл>" << endl;
        cerr << "Пример: " << argv[0] << " dump.txt data/boolean_index.idx" << endl;
        return 1;
    }
    
    string input_file = argv[1];
    string output_file = argv[2];
    
    cout << "Построение индекса из файла: " << input_file << endl;
    cout << "Выходной файл: " << output_file << endl;
    
    if (buildIndex(input_file, output_file)) {
        cout << "Индекс успешно построен и сохранен в " << output_file << endl;
        return 0;
    } else {
        cerr << "Ошибка при построении индекса!" << endl;
        return 2;
    }
}