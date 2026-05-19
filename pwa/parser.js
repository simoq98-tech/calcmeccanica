'use strict';

/**
 * Recursive-descent parser for math expressions.
 * Mirrors the Java Parser in CalculatorApp.java.
 *
 * Grammar:
 *   expr   = term (('+'|'−'|'-') term)*
 *   term   = pct  (('×'|'*'|'÷'|'/' | implicit) pct)*
 *   pct    = pow  ('%')*
 *   pow    = unary ('^' unary)?
 *   unary  = ('−'|'-'|'+')* fact
 *   fact   = atom ('!')*
 *   atom   = number | 'π' | name | '(' expr ')' | '√' atom | name '(' expr ')'
 *
 * Numbers support scientific notation (e.g. 1.5e-6).
 */
class Parser {
  constructor(s, radians, vars) {
    this.s = s;
    this.radians = !!radians;
    this.vars = vars || {};
    this.pos = 0;
  }

  parse() {
    const v = this.parseExpr();
    if (this.pos < this.s.length) {
      throw new Error("Inatteso '" + this.s[this.pos] + "'");
    }
    return v;
  }

  peek() { return this.pos < this.s.length ? this.s[this.pos] : '\0'; }
  match(c) { if (this.peek() === c) { this.pos++; return true; } return false; }

  parseExpr() {
    let v = this.parseTerm();
    while (true) {
      const c = this.peek();
      if (c === '+') { this.pos++; v += this.parseTerm(); }
      else if (c === '−' || c === '-') { this.pos++; v -= this.parseTerm(); }
      else break;
    }
    return v;
  }

  parseTerm() {
    let v = this.parsePct();
    while (true) {
      const c = this.peek();
      if (c === '×' || c === '*') { this.pos++; v *= this.parsePct(); }
      else if (c === '÷' || c === '/') { this.pos++; v /= this.parsePct(); }
      else if (this.isAtomStart(c)) { v *= this.parsePct(); }
      else break;
    }
    return v;
  }

  isAtomStart(c) {
    return (c >= '0' && c <= '9') || c === '(' || c === 'π' || c === '√'
        || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  parsePct() {
    let v = this.parsePow();
    while (this.peek() === '%') { this.pos++; v /= 100.0; }
    return v;
  }

  parsePow() {
    const base = this.parseUnary();
    if (this.peek() === '^') {
      this.pos++;
      return Math.pow(base, this.parseUnary());
    }
    return base;
  }

  parseUnary() {
    const c = this.peek();
    if (c === '−' || c === '-') { this.pos++; return -this.parseUnary(); }
    if (c === '+') { this.pos++; return this.parseUnary(); }
    return this.parseFact();
  }

  parseFact() {
    let v = this.parseAtom();
    while (this.peek() === '!') { this.pos++; v = this.factorial(v); }
    return v;
  }

  parseAtom() {
    const c = this.peek();
    if (c === '(') {
      this.pos++;
      const v = this.parseExpr();
      if (!this.match(')')) throw new Error("Manca ')'");
      return v;
    }
    if (c === 'π') { this.pos++; return Math.PI; }
    if (c === '√') { this.pos++; return Math.sqrt(this.parseAtom()); }
    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
      const name = this.readName();
      if (this.vars[name] !== undefined && this.peek() !== '(') return this.vars[name];
      if (name === 'e' && this.peek() !== '(') return Math.E;
      if (!this.match('(')) throw new Error("Atteso '(' dopo " + name);
      const arg = this.parseExpr();
      if (!this.match(')')) throw new Error("Manca ')'");
      return this.applyFunc(name, arg);
    }
    return this.readNumber();
  }

  readName() {
    const start = this.pos;
    while (this.pos < this.s.length) {
      const ch = this.s[this.pos];
      if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
          || (ch >= '0' && ch <= '9') || ch === '_') this.pos++;
      else break;
    }
    return this.s.substring(start, this.pos);
  }

  readNumber() {
    const start = this.pos;
    while (this.pos < this.s.length) {
      const ch = this.s[this.pos];
      if ((ch >= '0' && ch <= '9') || ch === '.') this.pos++;
      else break;
    }
    if (start === this.pos) throw new Error("Atteso numero");
    // Optional exponent: e/E [+/-/−] digits
    if (this.pos < this.s.length && (this.s[this.pos] === 'e' || this.s[this.pos] === 'E')) {
      let probe = this.pos + 1;
      let negExp = false;
      if (probe < this.s.length && (this.s[probe] === '+' || this.s[probe] === '-' || this.s[probe] === '−')) {
        negExp = this.s[probe] !== '+';
        probe++;
      }
      if (probe < this.s.length && this.s[probe] >= '0' && this.s[probe] <= '9') {
        const expStart = probe;
        while (probe < this.s.length && this.s[probe] >= '0' && this.s[probe] <= '9') probe++;
        const mantissa = this.s.substring(start, this.pos);
        const exp = this.s.substring(expStart, probe);
        this.pos = probe;
        return parseFloat(mantissa + 'e' + (negExp ? '-' : '') + exp);
      }
    }
    return parseFloat(this.s.substring(start, this.pos));
  }

  applyFunc(n, x) {
    switch (n) {
      case 'sin':  return Math.sin(this.radians ? x : x * Math.PI / 180);
      case 'cos':  return Math.cos(this.radians ? x : x * Math.PI / 180);
      case 'tan':  return Math.tan(this.radians ? x : x * Math.PI / 180);
      case 'asin': return this.radians ? Math.asin(x) : Math.asin(x) * 180 / Math.PI;
      case 'acos': return this.radians ? Math.acos(x) : Math.acos(x) * 180 / Math.PI;
      case 'atan': return this.radians ? Math.atan(x) : Math.atan(x) * 180 / Math.PI;
      case 'ln':   return Math.log(x);
      case 'log':  return Math.log10(x);
      case 'exp':  return Math.exp(x);
      case 'sqrt': return Math.sqrt(x);
      case 'abs':  return Math.abs(x);
      default: throw new Error("Funzione sconosciuta: " + n);
    }
  }

  factorial(v) {
    if (v < 0 || v !== Math.floor(v) || v > 170) throw new Error("Fattoriale non valido");
    let r = 1;
    for (let i = 2; i <= v; i++) r *= i;
    return r;
  }
}

window.Parser = Parser;
