import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import sys
import os


def fit_zipf(ranks, freqs):
    # Zipf: f = C / r^a  => log f = log C - a log r
    x = np.log(ranks)
    y = np.log(freqs)

    # линейная регрессия y = b0 + b1*x
    # b1 = -a, b0 = log C
    A = np.vstack([np.ones_like(x), x]).T
    b0, b1 = np.linalg.lstsq(A, y, rcond=None)[0]
    a = -b1
    C = np.exp(b0)

    pred = C / (ranks ** a)
    return C, a, pred


def fit_mandelbrot(ranks, freqs, b_min=0.0, b_max=2000.0, b_steps=200):
    """
    Mandelbrot: f(r) = C / (r + B)^a
      log f = log C - a * log(r + B)

    Подбор:
    - перебор B по сетке
    - для каждого B линейная регрессия по log(r+B)
    - выбираем B с минимальной SSE в log-пространстве
    """
    y = np.log(freqs)

    best = None
    best_sse = np.inf

    # сетка B: для больших корпусов B может быть сотни/тысячи
    Bs = np.linspace(b_min, b_max, b_steps)

    for B in Bs:
        x = np.log(ranks + B)

        A = np.vstack([np.ones_like(x), x]).T
        b0, b1 = np.linalg.lstsq(A, y, rcond=None)[0]
        a = -b1
        C = np.exp(b0)

        y_hat = b0 + b1 * x
        sse = np.sum((y - y_hat) ** 2)

        if sse < best_sse:
            best_sse = sse
            best = (C, a, B)

    C, a, B = best
    pred = C / ((ranks + B) ** a)
    return C, a, B, pred, best_sse


def r2_logspace(freqs, pred):
    y = np.log(freqs)
    yhat = np.log(pred)
    ss_res = np.sum((y - yhat) ** 2)
    ss_tot = np.sum((y - np.mean(y)) ** 2)
    return 1.0 - (ss_res / ss_tot)


def analyze_zipf(csv_file='results/frequencies.csv',
                 use_all=True,
                 max_points=None,
                 mandelbrot=True):
    if not os.path.exists(csv_file):
        print(f"Файл {csv_file} не найден!")
        return

    print("Анализ закона Ципфа / Мандельброта...")
    df = pd.read_csv(csv_file)

    # ВАЖНО: берем все слова (или ограничиваем max_points)
    if not use_all:
        top_n = min(1000, len(df))
        df = df.head(top_n)
    else:
        if max_points is not None:
            df = df.head(min(max_points, len(df)))

    ranks = df['Rank'].to_numpy(dtype=np.float64)
    freqs = df['Frequency'].to_numpy(dtype=np.float64)

    # защита от нулей (обычно их нет)
    mask = freqs > 0
    ranks = ranks[mask]
    freqs = freqs[mask]

    # ===== Zipf fit (обобщённый, с показателем a) =====
    zipf_C, zipf_a, zipf_pred = fit_zipf(ranks, freqs)
    zipf_r2 = r2_logspace(freqs, zipf_pred)

    # ===== Mandelbrot fit =====
    mand_res = None
    if mandelbrot:
        # Диапазон B можно подстроить:
        # для корпуса 30–50k документов обычно разумно 0..2000
        mC, mA, mB, m_pred, m_sse = fit_mandelbrot(
            ranks, freqs, b_min=0.0, b_max=2000.0, b_steps=250
        )
        m_r2 = r2_logspace(freqs, m_pred)
        mand_res = (mC, mA, mB, m_pred, m_r2)

    # ===== Plot (логарифмические оси) =====
    plt.figure(figsize=(12, 8))

    # Все точки — может быть очень много. Для красоты можно подсэмплировать хвост,
    # но ты просила "желательно для всех" — рисуем все (или ограниченные max_points).
    plt.scatter(ranks, freqs, alpha=0.35, s=6, label=f'Данные ({len(freqs):,} слов)')

    plt.plot(ranks, zipf_pred, linewidth=2, label=f'Zipf: f=C/r^a, a={zipf_a:.3f}, R²={zipf_r2:.4f}')

    if mand_res:
        mC, mA, mB, m_pred, m_r2 = mand_res
        plt.plot(ranks, m_pred, linewidth=2, label=f'Mandelbrot: f=C/(r+B)^a, B={mB:.1f}, a={mA:.3f}, R²={m_r2:.4f}')

    plt.xscale('log')
    plt.yscale('log')
    plt.xlabel('Ранг r (log)')
    plt.ylabel('Частота f (log)')
    title_suffix = "all words" if use_all else "top-1000"
    plt.title(f'Zipf / Mandelbrot ({title_suffix}, N={len(freqs):,})')
    plt.grid(True, which='both', alpha=0.25)
    plt.legend()
    plt.tight_layout()

    os.makedirs('results', exist_ok=True)
    plt.savefig('results/zipf_mandelbrot.png', dpi=300)
    plt.savefig('results/zipf_mandelbrot.pdf')


    # ===== Save report =====
    with open('results/zipf_analysis.txt', 'w', encoding='utf-8') as f:
        f.write("=== ZIPF / MANDELBROT ANALYSIS ===\n")
        f.write(f"Unique words used: {len(freqs)}\n")
        f.write(f"Top frequency: {int(freqs[0])}\n\n")

        f.write("ZIPF (generalized): f = C / r^a\n")
        f.write(f"  C = {zipf_C:.6e}\n")
        f.write(f"  a = {zipf_a:.6f}\n")
        f.write(f"  R2 (log-space) = {zipf_r2:.6f}\n\n")

        if mand_res:
            mC, mA, mB, m_pred, m_r2 = mand_res
            f.write("MANDELBROT: f = C / (r + B)^a\n")
            f.write(f"  C = {mC:.6e}\n")
            f.write(f"  a = {mA:.6f}\n")
            f.write(f"  B = {mB:.6f}\n")
            f.write(f"  R2 (log-space) = {m_r2:.6f}\n\n")

        f.write("Top-20 words:\n")
        f.write(f"{'Rank':<8}{'Frequency':<12}{'Word'}\n")
        f.write("-" * 60 + "\n")
        top_k = min(20, len(df))
        for i in range(top_k):
            f.write(f"{int(df.iloc[i]['Rank']):<8}{int(df.iloc[i]['Frequency']):<12}{df.iloc[i]['Word']}\n")

    print("\n=== РЕЗУЛЬТАТЫ ===")
    print(f"Слов использовано: {len(freqs):,}")
    print(f"Zipf: a={zipf_a:.3f}, C={zipf_C:.3e}, R²={zipf_r2:.4f}")
    if mand_res:
        mC, mA, mB, m_pred, m_r2 = mand_res
        print(f"Mandelbrot: a={mA:.3f}, B={mB:.1f}, C={mC:.3e}, R²={m_r2:.4f}")

    print("\nФайлы:")
    print("  results/zipf_mandelbrot.png")
    print("  results/zipf_mandelbrot.pdf")
    print("  results/zipf_analysis.txt")


if __name__ == "__main__":
    csv_file = sys.argv[1] if len(sys.argv) > 1 else 'results/frequencies.csv'
    # По умолчанию строим по всем словам; если очень тяжело — поставь max_points=200000, например
    analyze_zipf(csv_file, use_all=True, max_points=None, mandelbrot=True)