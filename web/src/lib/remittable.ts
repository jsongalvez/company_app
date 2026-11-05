/** What can go on a remittance. */
export function remittable<T>(items: readonly T[], amount: (item: T) => string): T[] {
    return items.filter((i) => Number(amount(i)) > 0);
}
