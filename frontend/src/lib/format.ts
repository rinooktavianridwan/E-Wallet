const rupiah = new Intl.NumberFormat('id-ID', {
  style: 'currency',
  currency: 'IDR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatRupiah(value: number): string {
  // Intl memakai spasi tak-putus setelah "Rp"; ganti agar mudah disalin dan dites.
  return rupiah.format(value).replace(/ /g, ' ')
}

const dateTime = new Intl.DateTimeFormat('id-ID', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso))
}

export const MIN_AMOUNT = 0.01
export const MAX_AMOUNT = 100_000_000

/**
 * Membaca nominal yang diketik pengguna memakai format Indonesia.
 * Menerima "50000", "50.000", "50.000,50", "50000,5", dan "50000.5".
 * Mengembalikan null bila bukan angka yang valid (maksimal 2 desimal).
 */
export function parseAmount(input: string): number | null {
  const text = input.trim()
  if (text === '') return null

  let normalized: string
  if (/^\d{1,3}(\.\d{3})+(,\d{1,2})?$/.test(text)) {
    normalized = text.replace(/\./g, '').replace(',', '.')
  } else if (/^\d+([.,]\d{1,2})?$/.test(text)) {
    normalized = text.replace(',', '.')
  } else {
    return null
  }

  const value = Number(normalized)
  return Number.isFinite(value) ? value : null
}

export function validateAmount(input: string): string | null {
  const value = parseAmount(input)
  if (value === null) return 'Tulis nominal dengan angka, maksimal 2 angka di belakang koma.'
  if (value < MIN_AMOUNT) return 'Nominal minimal Rp 0,01.'
  if (value > MAX_AMOUNT) return 'Nominal maksimal Rp 100.000.000,00.'
  return null
}
