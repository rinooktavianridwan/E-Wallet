import { describe, expect, it } from 'vitest'
import { formatRupiah, parseAmount, validateAmount } from './format'

describe('parseAmount', () => {
  it.each([
    ['50000', 50000],
    ['50.000', 50000],
    ['1.250.000', 1250000],
    ['50.000,50', 50000.5],
    ['50000,5', 50000.5],
    ['50000.25', 50000.25],
    ['  75  ', 75],
  ])('membaca %s sebagai %d', (input, expected) => {
    expect(parseAmount(input)).toBe(expected)
  })

  it.each(['', 'abc', '12,345', '1.2.3', '-5', '5,', '1e3', '10.000.00'])('menolak %j', (input) => {
    expect(parseAmount(input)).toBeNull()
  })
})

describe('validateAmount', () => {
  it('menerima nominal yang sah', () => {
    expect(validateAmount('50.000')).toBeNull()
    expect(validateAmount('0,01')).toBeNull()
    expect(validateAmount('100.000.000')).toBeNull()
  })

  it('menolak nol, terlalu besar, dan bukan angka', () => {
    expect(validateAmount('0')).toMatch(/minimal/)
    expect(validateAmount('100.000.001')).toMatch(/maksimal/)
    expect(validateAmount('lima ribu')).toMatch(/angka/)
  })
})

describe('formatRupiah', () => {
  it('memakai format Indonesia dengan dua desimal', () => {
    expect(formatRupiah(1250000.5)).toBe('Rp 1.250.000,50')
    expect(formatRupiah(0)).toBe('Rp 0,00')
  })
})
