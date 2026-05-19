package calc;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Sections {
    private Sections() {}

    /**
     * Risultato calcolo proprietà sezione (unità mm e mm⁴ etc).
     */
    public record SectionResult(
        double A,         // mm²
        double Ix,        // mm⁴
        double Iy,        // mm⁴
        double Wx,        // mm³
        double Wy,        // mm³
        double ix,        // mm
        double iy,        // mm
        double perimeter, // mm
        double yc,        // distanza centroide dal basso, mm
        double xc         // distanza centroide da sinistra, mm
    ) {
        public Map<String, Double> asMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("A",  A);
            m.put("Ix", Ix);
            m.put("Iy", Iy);
            m.put("Wx", Wx);
            m.put("Wy", Wy);
            m.put("ix", ix);
            m.put("iy", iy);
            m.put("perimetro", perimeter);
            m.put("yc", yc);
            m.put("xc", xc);
            return m;
        }
    }

    public enum Shape { RETTANGOLO, RETTANGOLO_CAVO, CERCHIO, TUBO, PROFILO_T, PROFILO_L }

    public static SectionResult rectangle(double b, double h) {
        double A  = b*h;
        double Ix = b*h*h*h/12.0;
        double Iy = h*b*b*b/12.0;
        double Wx = b*h*h/6.0;
        double Wy = h*b*b/6.0;
        double ix = h/(2*Math.sqrt(3));
        double iy = b/(2*Math.sqrt(3));
        return new SectionResult(A, Ix, Iy, Wx, Wy, ix, iy, 2*(b+h), h/2.0, b/2.0);
    }

    public static SectionResult hollowRect(double b, double h, double t) {
        if (2*t >= Math.min(b,h)) throw new IllegalArgumentException("Spessore troppo grande");
        double bi = b - 2*t, hi = h - 2*t;
        double A  = b*h - bi*hi;
        double Ix = (b*h*h*h - bi*hi*hi*hi) / 12.0;
        double Iy = (h*b*b*b - hi*bi*bi*bi) / 12.0;
        double Wx = Ix / (h/2.0);
        double Wy = Iy / (b/2.0);
        double ix = Math.sqrt(Ix/A);
        double iy = Math.sqrt(Iy/A);
        return new SectionResult(A, Ix, Iy, Wx, Wy, ix, iy, 2*(b+h) + 2*(bi+hi), h/2.0, b/2.0);
    }

    public static SectionResult solidCircle(double D) {
        double A = Math.PI*D*D/4.0;
        double I = Math.PI*Math.pow(D,4)/64.0;
        double W = Math.PI*D*D*D/32.0;
        double i = D/4.0;
        return new SectionResult(A, I, I, W, W, i, i, Math.PI*D, D/2.0, D/2.0);
    }

    public static SectionResult tube(double De, double t) {
        double Di = De - 2*t;
        if (Di < 0) throw new IllegalArgumentException("Spessore troppo grande");
        double A = Math.PI*(De*De - Di*Di)/4.0;
        double I = Math.PI*(Math.pow(De,4) - Math.pow(Di,4))/64.0;
        double W = I/(De/2.0);
        double i = Math.sqrt(I/A);
        return new SectionResult(A, I, I, W, W, i, i, Math.PI*(De+Di), De/2.0, De/2.0);
    }

    /** Profilo T: flangia b×tf in alto, anima tw×(h-tf) sotto. y misurata dal basso. */
    public static SectionResult tProfile(double b, double h, double tf, double tw) {
        if (tf >= h) throw new IllegalArgumentException("Spessore flangia >= h");
        if (tw >= b) throw new IllegalArgumentException("Spessore anima >= b");
        double A1 = b*tf;        double y1 = h - tf/2.0;
        double A2 = tw*(h - tf); double y2 = (h - tf)/2.0;
        double A  = A1 + A2;
        double yc = (A1*y1 + A2*y2) / A;
        double Ix = (b*tf*tf*tf/12.0 + A1*(y1-yc)*(y1-yc))
                 + (tw*Math.pow(h-tf,3)/12.0 + A2*(y2-yc)*(y2-yc));
        double Iy = tf*b*b*b/12.0 + (h-tf)*tw*tw*tw/12.0;
        double Wx = Ix / Math.max(yc, h - yc);
        double Wy = Iy / (b/2.0);
        double ix = Math.sqrt(Ix/A);
        double iy = Math.sqrt(Iy/A);
        return new SectionResult(A, Ix, Iy, Wx, Wy, ix, iy, 2*(b+h), yc, b/2.0);
    }

    /** Profilo L equilatero: ali a × t. Calcolo rispetto ad assi geometrici (non principali). */
    public static SectionResult lProfile(double a, double t) {
        if (t >= a) throw new IllegalArgumentException("Spessore >= dimensione ala");
        // Composto da: orizzontale (a × t, in basso) + verticale (t × (a-t), sopra angolo)
        double A1 = a*t;          double x1 = a/2.0;      double y1 = t/2.0;
        double A2 = t*(a-t);      double x2 = t/2.0;      double y2 = t + (a-t)/2.0;
        double A  = A1 + A2;
        double xc = (A1*x1 + A2*x2)/A;
        double yc = (A1*y1 + A2*y2)/A;
        double Ix = (a*t*t*t/12.0 + A1*(y1-yc)*(y1-yc))
                 + (t*Math.pow(a-t,3)/12.0 + A2*(y2-yc)*(y2-yc));
        double Iy = (t*a*a*a/12.0 + A1*(x1-xc)*(x1-xc))
                 + ((a-t)*t*t*t/12.0 + A2*(x2-xc)*(x2-xc));
        double Wx = Ix / Math.max(yc, a - yc);
        double Wy = Iy / Math.max(xc, a - xc);
        double ix = Math.sqrt(Ix/A);
        double iy = Math.sqrt(Iy/A);
        // perimetro esterno L: 2a + 2t (approx)
        double perim = 2*a + 2*t + 2*(a-t);
        return new SectionResult(A, Ix, Iy, Wx, Wy, ix, iy, perim, yc, xc);
    }
}
