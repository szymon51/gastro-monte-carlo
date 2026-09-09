[![Live Demo](https://img.shields.io/badge/demo-live-brightgreen)](https://gastro-monte-carlo-production.up.railway.app)
# Gastro Monte Carlo

Symulacja Monte Carlo do optymalizacji zatowarowania na stoisku gastro (ciasta, słoiki, pierogi).
Zamiast zgadywać ile produkować dziennie, program symuluje tysiące wariantów tygodnia
z losowym popytem i szuka poziomu zatowarowania, który maksymalizuje zysk skorygowany o ryzyko.

## Problem

Klasyczny newsvendor problem: produkujesz za mało — tracisz sprzedaż, produkujesz za dużo —
marnujesz towar (zwłaszcza przy krótkim terminie ważności). Popyt jest niepewny i różni się
w zależności od dnia tygodnia. Chcemy znaleźć poziom zatowarowania, który dobrze balansuje
oczekiwany zysk i ryzyko złego tygodnia.

## Jak to działa

1. Popyt na każdy dzień losowany jest z rozkładu normalnego, z sezonowością (mnożnik na
   dzień tygodnia — słabiej we wtorek, mocno w sobotę).
2. Magazyn działa na zasadzie FIFO z uwzględnieniem terminu ważności — towar starzeje się
   i po przekroczeniu shelf life jest liczony jako strata.
3. Restock policy decyduje ile dziś dopiec na podstawie aktualnego stanu magazynu (base-stock
   policy: dopełniaj do poziomu S; albo S ustawione osobno na każdy dzień tygodnia).
4. Dla każdego testowanego poziomu S symulujemy 20 000 tygodni i liczymy: średni zysk,
   odchylenie standardowe, CVaR 5% (średni wynik w najgorszych scenariuszach), ryzyko
   braku towaru, średnie marnowanie.
5. Wybieramy S maksymalizujące `zysk - 0.5 × odchylenie` (kara za ryzyko).
6. Grid search jest dwuetapowy: najpierw zgrubny (żeby znaleźć okolicę optimum), potem
   dokładny wokół znalezionego punktu (żeby nie tracić czasu na cały zakres).

## Stack

Java 17 / Spring Boot / Thymeleaf. Zero baz danych — wszystko liczone na żądanie,
w pamięci, przy każdym odświeżeniu strony.

## Co można zrobić w interfejsie

- Wybrać produkt i zobaczyć rekomendowany poziom zatowarowania z pełnym uzasadnieniem
  (zysk, ryzyko, marnowanie, szansa na brak towaru).
- Ustawić własną strategię (inny poziom na każdy dzień tygodnia) i porównać z rekomendacją
  algorytmu — liczby na tych samych scenariuszach popytu (common random numbers), więc
  różnica w wyniku to realna różnica strategii, nie szum.
- Sprawdzić what-if: co się zmieni, jeśli popyt w danym dniu wzrośnie/spadnie o X%
  (np. przez pogodę albo wydarzenie w mieście).

## Co jest uproszczone (świadomie)

- Popyt na różne produkty losowany jest niezależnie — brak korelacji między nimi
  (w realu dobra sobota podbija sprzedaż wszystkiego naraz, nie tylko jednego produktu).
- Dla produktów z długim terminem ważności (np. słoiki, 60 dni) tydzień symulacji to
  za krótki horyzont, żeby złapać realne marnowanie — metryka "śr. marnowanie" dla
  takich produktów jest zawyżona (liczy cały koniec-tygodnia zapas jako stratę, nawet
  jeśli fizycznie nic by się nie zepsuło).
- Parametry popytu (średnia, odchylenie) są wpisane na sztywno w kodzie, nie pochodzą
  z rzeczywistych danych sprzedażowych.
- Restock policy z osobnym S na każdy dzień trzeba dostroić ręcznie — program nie szuka
  automatycznie optymalnej kombinacji 5 wartości naraz (to eksplodowałoby obliczeniowo).

## Struktura kodu

- `Inventory` — magazyn z FIFO i starzeniem się towaru
- `RestockPolicy` — strategia decydowania ile produkować (base-stock albo per dzień)
- `DemandModel` — strategia generowania popytu (stały, sezonowy, sezonowy ze skalowaniem wariancji)
- `SimulationService` — silnik Monte Carlo, grid search
- `SimulationController` — wiąże to wszystko z formularzami i widokiem