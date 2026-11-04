export const currencyCodes = Intl.supportedValuesOf("currency").sort();

export function getCurrencies(locale: string) {
  const currencyNames = new Intl.DisplayNames([locale], { type: "currency" });
  return currencyCodes.map((code) => ({
    code,
    label: `${code} — ${currencyNames.of(code) ?? code}`,
  }));
}
