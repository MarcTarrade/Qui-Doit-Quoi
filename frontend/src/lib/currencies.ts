const currencyNames = new Intl.DisplayNames(["en"], { type: "currency" });

export const currencies = Intl.supportedValuesOf("currency")
  .sort()
  .map((code) => ({
    code,
    label: `${code} — ${currencyNames.of(code) ?? code}`,
  }));
