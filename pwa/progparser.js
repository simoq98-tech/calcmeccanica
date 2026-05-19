'use strict';

/**
 * Programmer-mode parser (integer-only, BigInt).
 *
 * Precedence (lowest first):
 *   expr  = or
 *   or    = xor (OR xor)*
 *   xor   = and (XOR and)*
 *   and   = shift (AND shift)*
 *   shift = add (('<<'|'>>') add)*
 *   add   = mul (('+'|'-') mul)*
 *   mul   = unary (('*'|'/') unary)*
 *   unary = ('NOT' | '-')* atom
 *   atom  = number | '(' expr ')'
 */
class ProgParser {
  constructor(s, base) {
    this.s = String(s);
    this.base = base;
    this.pos = 0;
  }

  parse() {
    const v = this.parseOr();
    this.skipWs();
    if (this.pos < this.s.length) throw new Error("Inatteso '" + this.s[this.pos] + "'");
    return v;
  }

  skipWs() {
    while (this.pos < this.s.length && /\s/.test(this.s[this.pos])) this.pos++;
  }

  match(c) {
    this.skipWs();
    if (this.pos < this.s.length && this.s[this.pos] === c) { this.pos++; return true; }
    return false;
  }

  matchStr(w) {
    this.skipWs();
    if (this.s.substr(this.pos, w.length) === w) { this.pos += w.length; return true; }
    return false;
  }

  matchWord(w) {
    this.skipWs();
    if (this.s.substr(this.pos, w.length) === w) {
      const end = this.pos + w.length;
      if (end < this.s.length && this.isDigitForBase(this.s[end])) return false;
      this.pos = end;
      return true;
    }
    return false;
  }

  isDigitForBase(c) {
    switch (this.base) {
      case 16: return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
      case 10: return c >= '0' && c <= '9';
      case 8:  return c >= '0' && c <= '7';
      case 2:  return c === '0' || c === '1';
      default: return false;
    }
  }

  parseOr() {
    let v = this.parseXor();
    while (this.matchWord('OR')) v = v | this.parseXor();
    return v;
  }
  parseXor() {
    let v = this.parseAnd();
    while (this.matchWord('XOR')) v = v ^ this.parseAnd();
    return v;
  }
  parseAnd() {
    let v = this.parseShift();
    while (this.matchWord('AND')) v = v & this.parseShift();
    return v;
  }
  parseShift() {
    let v = this.parseAdd();
    while (true) {
      if (this.matchStr('<<'))      v = v << Number(this.parseAdd());
      else if (this.matchStr('>>')) v = v >> Number(this.parseAdd());
      else break;
    }
    return v;
  }
  parseAdd() {
    let v = this.parseMul();
    while (true) {
      this.skipWs();
      if (this.match('+'))                                   v = v + this.parseMul();
      else if (this.match('-') || this.match('−'))           v = v - this.parseMul();
      else break;
    }
    return v;
  }
  parseMul() {
    let v = this.parseUnary();
    while (true) {
      this.skipWs();
      if (this.match('×') || this.match('*'))                v = v * this.parseUnary();
      else if (this.match('÷') || this.match('/'))           v = v / this.parseUnary();
      else break;
    }
    return v;
  }
  parseUnary() {
    if (this.matchWord('NOT')) return ~this.parseUnary();
    this.skipWs();
    if (this.pos < this.s.length && (this.s[this.pos] === '-' || this.s[this.pos] === '−')) {
      this.pos++;
      return -this.parseUnary();
    }
    return this.parseAtom();
  }
  parseAtom() {
    this.skipWs();
    if (this.match('(')) {
      const v = this.parseOr();
      if (!this.match(')')) throw new Error("Manca ')'");
      return v;
    }
    const start = this.pos;
    while (this.pos < this.s.length && this.isDigitForBase(this.s[this.pos])) this.pos++;
    if (start === this.pos) throw new Error('Atteso numero');
    return parseInt(this.s.substring(start, this.pos), this.base);
  }
}

window.ProgParser = ProgParser;
