/** What can go on a remittance. A line must carry money, so a ₱0 session or sale is never offered. */
export function remittable<T>(items: readonly T[], amount: (item: T) => string): T[] {
    return items.filter((i) => Number(amount(i)) > 0);
}
