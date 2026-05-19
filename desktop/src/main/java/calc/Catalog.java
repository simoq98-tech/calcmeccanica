package calc;

import java.util.List;

public final class Catalog {
    private Catalog() {}

    // ====================== Materiali ======================
    public record Material(
        String name,
        double rho,           // kg/m³
        double E,             // MPa
        double G,             // MPa
        double nu,            // -
        double yieldStress,   // MPa
        double tensileStress, // MPa
        double alpha          // 1/K × 10^-6
    ) {}

    public static final List<Material> MATERIALS = List.of(
        new Material("S235JR (acciaio strutturale)", 7850, 210000, 80800, 0.30, 235, 360, 12.0),
        new Material("S275JR (acciaio strutturale)", 7850, 210000, 80800, 0.30, 275, 410, 12.0),
        new Material("S355JR (acciaio strutturale)", 7850, 210000, 80800, 0.30, 355, 470, 12.0),
        new Material("C40 (acciaio bonificato)",     7850, 210000, 80800, 0.30, 320, 590, 12.0),
        new Material("C45 (acciaio bonificato)",     7850, 210000, 80800, 0.30, 340, 610, 12.0),
        new Material("AISI 304L (inox)",             8000, 193000, 75000, 0.29, 210, 520, 17.3),
        new Material("AISI 316L (inox)",             8000, 193000, 75000, 0.29, 220, 530, 16.5),
        new Material("AISI 4140 (acc. legato)",      7850, 205000, 80000, 0.30, 655, 950, 12.3),
        new Material("Al 6061-T6",                   2700,  69000, 26000, 0.33, 276, 310, 23.6),
        new Material("Al 7075-T6",                   2810,  71700, 26900, 0.33, 503, 572, 23.6),
        new Material("Ottone CW508L",                8500, 100000, 37000, 0.34, 200, 360, 19.0),
        new Material("Rame Cu-ETP",                  8930, 117000, 44000, 0.34,  70, 220, 16.5),
        new Material("Ghisa GJL-250",                7200, 110000, 43000, 0.27,   0, 250, 10.4),
        new Material("Titanio Ti-6Al-4V",            4430, 113800, 44000, 0.34, 880, 950,  8.6),
        new Material("PTFE (Teflon)",                2150,    500,   200, 0.46,   9,  25,100.0),
        new Material("Polietilene HDPE",              950,   1100,   410, 0.46,  27,  32,200.0)
    );

    // ====================== Pipe schedule (ASME B36.10) ======================
    public record Pipe(
        String dn, String nps, double od, String sch, double wall, double weight
    ) {
        public double id() { return od - 2*wall; }
        public double volPerM() { double r = id()/1000.0/2.0; return Math.PI*r*r*1000.0; } // L/m
    }

    public static final List<Pipe> PIPES = List.of(
        new Pipe("DN15",  "1/2\"",     21.3, "Sch 40",  2.77,  1.27),
        new Pipe("DN15",  "1/2\"",     21.3, "Sch 80",  3.73,  1.62),
        new Pipe("DN20",  "3/4\"",     26.7, "Sch 40",  2.87,  1.69),
        new Pipe("DN20",  "3/4\"",     26.7, "Sch 80",  3.91,  2.20),
        new Pipe("DN25",  "1\"",       33.4, "Sch 40",  3.38,  2.50),
        new Pipe("DN25",  "1\"",       33.4, "Sch 80",  4.55,  3.24),
        new Pipe("DN32",  "1-1/4\"",   42.2, "Sch 40",  3.56,  3.39),
        new Pipe("DN32",  "1-1/4\"",   42.2, "Sch 80",  4.85,  4.47),
        new Pipe("DN40",  "1-1/2\"",   48.3, "Sch 40",  3.68,  4.05),
        new Pipe("DN40",  "1-1/2\"",   48.3, "Sch 80",  5.08,  5.41),
        new Pipe("DN50",  "2\"",       60.3, "Sch 40",  3.91,  5.44),
        new Pipe("DN50",  "2\"",       60.3, "Sch 80",  5.54,  7.48),
        new Pipe("DN65",  "2-1/2\"",   73.0, "Sch 40",  5.16,  8.63),
        new Pipe("DN65",  "2-1/2\"",   73.0, "Sch 80",  7.01, 11.41),
        new Pipe("DN80",  "3\"",       88.9, "Sch 40",  5.49, 11.29),
        new Pipe("DN80",  "3\"",       88.9, "Sch 80",  7.62, 15.27),
        new Pipe("DN100", "4\"",      114.3, "Sch 40",  6.02, 16.07),
        new Pipe("DN100", "4\"",      114.3, "Sch 80",  8.56, 22.31),
        new Pipe("DN125", "5\"",      141.3, "Sch 40",  6.55, 21.77),
        new Pipe("DN125", "5\"",      141.3, "Sch 80",  9.53, 30.94),
        new Pipe("DN150", "6\"",      168.3, "Sch 40",  7.11, 28.26),
        new Pipe("DN150", "6\"",      168.3, "Sch 80", 10.97, 42.55),
        new Pipe("DN200", "8\"",      219.1, "Sch 40",  8.18, 42.55),
        new Pipe("DN200", "8\"",      219.1, "Sch 80", 12.70, 64.64),
        new Pipe("DN250", "10\"",     273.0, "Sch 40",  9.27, 60.31),
        new Pipe("DN250", "10\"",     273.0, "Sch 80", 15.09, 96.01),
        new Pipe("DN300", "12\"",     323.9, "Sch 40", 10.31, 79.73),
        new Pipe("DN300", "12\"",     323.9, "Sch 80", 17.48,132.08)
    );

    // ====================== Unit categories ======================
    public record UnitDef(String name, String symbol, double toBase) {}
    public record UnitCategory(String name, List<UnitDef> units, boolean special) {
        public UnitCategory(String name, List<UnitDef> units) { this(name, units, false); }
    }

    public static final List<UnitCategory> CATEGORIES = List.of(
        new UnitCategory("Lunghezza", List.of(
            new UnitDef("metro",        "m",     1.0),
            new UnitDef("millimetro",   "mm",    1e-3),
            new UnitDef("centimetro",   "cm",    1e-2),
            new UnitDef("decimetro",    "dm",    1e-1),
            new UnitDef("kilometro",    "km",    1000.0),
            new UnitDef("micrometro",   "µm",    1e-6),
            new UnitDef("pollice",      "in",    0.0254),
            new UnitDef("piede",        "ft",    0.3048),
            new UnitDef("yard",         "yd",    0.9144),
            new UnitDef("miglio",       "mi",    1609.344)
        )),
        new UnitCategory("Massa", List.of(
            new UnitDef("kilogrammo", "kg",  1.0),
            new UnitDef("grammo",     "g",   1e-3),
            new UnitDef("milligrammo","mg",  1e-6),
            new UnitDef("tonnellata", "t",   1000.0),
            new UnitDef("oncia",      "oz",  0.0283495),
            new UnitDef("libbra",     "lb",  0.453592)
        )),
        new UnitCategory("Forza", List.of(
            new UnitDef("Newton",     "N",   1.0),
            new UnitDef("kiloNewton", "kN",  1000.0),
            new UnitDef("MegaNewton", "MN",  1e6),
            new UnitDef("kgf",        "kgf", 9.80665),
            new UnitDef("lbf",        "lbf", 4.44822),
            new UnitDef("dina",       "dyn", 1e-5)
        )),
        new UnitCategory("Pressione/Sforzo", List.of(
            new UnitDef("Pascal",     "Pa",   1.0),
            new UnitDef("kiloPascal", "kPa",  1000.0),
            new UnitDef("megaPascal", "MPa",  1e6),
            new UnitDef("gigaPascal", "GPa",  1e9),
            new UnitDef("N/mm²",      "N/mm²",1e6),
            new UnitDef("bar",        "bar",  1e5),
            new UnitDef("millibar",   "mbar", 100.0),
            new UnitDef("psi",        "psi",  6894.76),
            new UnitDef("ksi",        "ksi",  6.89476e6),
            new UnitDef("atmosfera",  "atm",  101325.0),
            new UnitDef("mmHg",       "mmHg", 133.322)
        )),
        new UnitCategory("Coppia", List.of(
            new UnitDef("Newton-metro", "N·m",   1.0),
            new UnitDef("Newton-mm",    "N·mm",  1e-3),
            new UnitDef("kN-metro",     "kN·m",  1000.0),
            new UnitDef("kgf-metro",    "kgf·m", 9.80665),
            new UnitDef("lbf-piede",    "lbf·ft",1.35582),
            new UnitDef("lbf-pollice",  "lbf·in",0.112985)
        )),
        new UnitCategory("Energia", List.of(
            new UnitDef("Joule",       "J",    1.0),
            new UnitDef("kiloJoule",   "kJ",   1000.0),
            new UnitDef("megaJoule",   "MJ",   1e6),
            new UnitDef("Wattora",     "Wh",   3600.0),
            new UnitDef("kilowattora", "kWh",  3.6e6),
            new UnitDef("kilocaloria", "kcal", 4184.0),
            new UnitDef("BTU",         "BTU",  1055.06)
        )),
        new UnitCategory("Potenza", List.of(
            new UnitDef("Watt",      "W",  1.0),
            new UnitDef("kiloWatt",  "kW", 1000.0),
            new UnitDef("megaWatt",  "MW", 1e6),
            new UnitDef("cav. vap.", "CV", 735.499),
            new UnitDef("horsepower","HP", 745.7)
        )),
        new UnitCategory("Velocità", List.of(
            new UnitDef("metri/sec.", "m/s",  1.0),
            new UnitDef("km/ora",     "km/h", 1.0/3.6),
            new UnitDef("piedi/sec.", "ft/s", 0.3048),
            new UnitDef("miglia/ora", "mph",  0.44704),
            new UnitDef("nodi",       "kn",   0.514444)
        )),
        new UnitCategory("Area", List.of(
            new UnitDef("metro²", "m²",  1.0),
            new UnitDef("mm²",    "mm²", 1e-6),
            new UnitDef("cm²",    "cm²", 1e-4),
            new UnitDef("km²",    "km²", 1e6),
            new UnitDef("ettaro", "ha",  1e4),
            new UnitDef("in²",    "in²", 6.4516e-4),
            new UnitDef("ft²",    "ft²", 0.092903)
        )),
        new UnitCategory("Volume", List.of(
            new UnitDef("metro³",   "m³",  1.0),
            new UnitDef("litro",    "L",   1e-3),
            new UnitDef("millilitro","mL", 1e-6),
            new UnitDef("mm³",      "mm³", 1e-9),
            new UnitDef("cm³",      "cm³", 1e-6),
            new UnitDef("in³",      "in³", 1.63871e-5),
            new UnitDef("ft³",      "ft³", 0.0283168),
            new UnitDef("gallone US","gal",3.78541e-3)
        )),
        new UnitCategory("Angolo", List.of(
            new UnitDef("radiante",  "rad", 1.0),
            new UnitDef("grado",     "°",   Math.PI/180.0),
            new UnitDef("gon",       "gon", Math.PI/200.0),
            new UnitDef("giro",      "rev", 2*Math.PI),
            new UnitDef("primo",     "'",   Math.PI/(180.0*60)),
            new UnitDef("secondo",   "\"",  Math.PI/(180.0*3600))
        )),
        new UnitCategory("Frequenza", List.of(
            new UnitDef("Hertz",  "Hz",  1.0),
            new UnitDef("kHz",    "kHz", 1000.0),
            new UnitDef("MHz",    "MHz", 1e6),
            new UnitDef("RPM",    "rpm", 1.0/60.0)
        )),
        new UnitCategory("Densità", List.of(
            new UnitDef("kg/m³",   "kg/m³",  1.0),
            new UnitDef("g/cm³",   "g/cm³",  1000.0),
            new UnitDef("kg/L",    "kg/L",   1000.0),
            new UnitDef("lb/in³",  "lb/in³", 27679.9),
            new UnitDef("lb/ft³",  "lb/ft³", 16.0185)
        )),
        new UnitCategory("Temperatura", List.of(
            new UnitDef("Celsius",    "°C", 0),
            new UnitDef("Kelvin",     "K",  0),
            new UnitDef("Fahrenheit", "°F", 0)
        ), true)
    );

    // ====================== Bolt ISO threads ======================
    public record Bolt(String designation, double d, double pitch, double As, double dTap) {}

    /** ISO metric coarse threads (UNI ISO 724/261). d=nominal mm, pitch mm, As=stress area mm², dTap=tap drill mm */
    public static final List<Bolt> BOLTS = List.of(
        new Bolt("M3",   3.0,  0.50,   5.03,  2.50),
        new Bolt("M4",   4.0,  0.70,   8.78,  3.30),
        new Bolt("M5",   5.0,  0.80,  14.18,  4.20),
        new Bolt("M6",   6.0,  1.00,  20.12,  5.00),
        new Bolt("M8",   8.0,  1.25,  36.61,  6.80),
        new Bolt("M10", 10.0,  1.50,  57.99,  8.50),
        new Bolt("M12", 12.0,  1.75,  84.27, 10.20),
        new Bolt("M14", 14.0,  2.00, 115.44, 12.00),
        new Bolt("M16", 16.0,  2.00, 156.67, 14.00),
        new Bolt("M18", 18.0,  2.50, 192.47, 15.50),
        new Bolt("M20", 20.0,  2.50, 244.79, 17.50),
        new Bolt("M22", 22.0,  2.50, 303.40, 19.50),
        new Bolt("M24", 24.0,  3.00, 352.50, 21.00),
        new Bolt("M27", 27.0,  3.00, 459.41, 24.00),
        new Bolt("M30", 30.0,  3.50, 560.59, 26.50),
        new Bolt("M33", 33.0,  3.50, 693.55, 29.50),
        new Bolt("M36", 36.0,  4.00, 816.72, 32.00),
        new Bolt("M42", 42.0,  4.50,1120.91, 37.50),
        new Bolt("M48", 48.0,  5.00,1473.13, 43.00)
    );

    public record BoltClass(String name, double yieldMPa, double tensileMPa) {}

    public static final List<BoltClass> BOLT_CLASSES = List.of(
        new BoltClass("4.6",  240,  400),
        new BoltClass("5.6",  300,  500),
        new BoltClass("8.8",  640,  800),
        new BoltClass("10.9", 900, 1000),
        new BoltClass("12.9",1080, 1200)
    );

    // ====================== Commercial steel profiles ======================
    public record Profile(
        String designation,
        double h, double b, double tw, double tf,  // mm
        double A,     // mm²
        double Ix,    // mm⁴ (about strong axis)
        double Iy,    // mm⁴
        double Wx,    // mm³
        double Wy,    // mm³
        double weight // kg/m
    ) {}

    /** IPE — EN 10365 (subset). */
    public static final List<Profile> IPE = List.of(
        new Profile("IPE 80",   80,  46, 3.8, 5.2,   764,    801200,    84900,   20030,   3691,  6.0),
        new Profile("IPE 100", 100,  55, 4.1, 5.7,  1032,   1710000,   159200,   34200,   5790,  8.1),
        new Profile("IPE 120", 120,  64, 4.4, 6.3,  1321,   3178000,   276700,   52960,   8650, 10.4),
        new Profile("IPE 140", 140,  73, 4.7, 6.9,  1643,   5412000,   449200,   77320,  12310, 12.9),
        new Profile("IPE 160", 160,  82, 5.0, 7.4,  2009,   8693000,   683100,  108700,  16660, 15.8),
        new Profile("IPE 180", 180,  91, 5.3, 8.0,  2395,  13170000,  1009000,  146300,  22160, 18.8),
        new Profile("IPE 200", 200, 100, 5.6, 8.5,  2848,  19430000,  1424000,  194300,  28470, 22.4),
        new Profile("IPE 220", 220, 110, 5.9, 9.2,  3337,  27720000,  2049000,  252000,  37250, 26.2),
        new Profile("IPE 240", 240, 120, 6.2, 9.8,  3912,  38920000,  2836000,  324300,  47270, 30.7),
        new Profile("IPE 270", 270, 135, 6.6,10.2,  4595,  57900000,  4199000,  428900,  62200, 36.1),
        new Profile("IPE 300", 300, 150, 7.1,10.7,  5381,  83560000,  6037000,  557100,  80500, 42.2),
        new Profile("IPE 330", 330, 160, 7.5,11.5,  6261, 117700000,  7881000,  713100,  98520, 49.1),
        new Profile("IPE 360", 360, 170, 8.0,12.7,  7273, 162700000, 10430000,  903600, 122800, 57.1),
        new Profile("IPE 400", 400, 180, 8.6,13.5,  8446, 231300000, 13180000, 1156000, 146400, 66.3),
        new Profile("IPE 450", 450, 190, 9.4,14.6,  9882, 337400000, 16760000, 1500000, 176400, 77.6),
        new Profile("IPE 500", 500, 200,10.2,16.0, 11550, 482000000, 21420000, 1928000, 214200, 90.7),
        new Profile("IPE 550", 550, 210,11.1,17.2, 13440, 671200000, 26680000, 2440000, 254100,106.0),
        new Profile("IPE 600", 600, 220,12.0,19.0, 15600, 920800000, 33870000, 3069000, 307900,122.0)
    );

    /** HEA — EN 10365 (subset). */
    public static final List<Profile> HEA = List.of(
        new Profile("HEA 100",  96, 100, 5.0, 8.0,  2124,   3492000,  1336000,   72760,  26720, 16.7),
        new Profile("HEA 120", 114, 120, 5.0, 8.0,  2534,   6062000,  2307000,  106400,  38480, 19.9),
        new Profile("HEA 140", 133, 140, 5.5, 8.5,  3142,  10330000,  3893000,  155400,  55620, 24.7),
        new Profile("HEA 160", 152, 160, 6.0, 9.0,  3877,  16730000,  6155000,  220100,  76950, 30.4),
        new Profile("HEA 180", 171, 180, 6.0, 9.5,  4525,  25100000,  9249000,  293600, 102700, 35.5),
        new Profile("HEA 200", 190, 200, 6.5,10.0,  5383,  36920000, 13360000,  388600, 133600, 42.3),
        new Profile("HEA 220", 210, 220, 7.0,11.0,  6434,  54100000, 19550000,  515200, 177700, 50.5),
        new Profile("HEA 240", 230, 240, 7.5,12.0,  7684,  77630000, 27690000,  674900, 230700, 60.3),
        new Profile("HEA 260", 250, 260, 7.5,12.5,  8682, 104500000, 36670000,  836400, 282100, 68.2),
        new Profile("HEA 280", 270, 280, 8.0,13.0,  9726, 136700000, 47630000, 1013000, 340200, 76.4),
        new Profile("HEA 300", 290, 300, 8.5,14.0, 11250, 182600000, 63100000, 1260000, 420600, 88.3)
    );

    /** HEB — EN 10365 (subset). */
    public static final List<Profile> HEB = List.of(
        new Profile("HEB 100", 100, 100, 6.0,10.0,  2604,   4495000,  1670000,   89910,  33420, 20.4),
        new Profile("HEB 120", 120, 120, 6.5,11.0,  3401,   8643000,  3176000,  144000,  52920, 26.7),
        new Profile("HEB 140", 140, 140, 7.0,12.0,  4296,  15090000,  5497000,  215600,  78530, 33.7),
        new Profile("HEB 160", 160, 160, 8.0,13.0,  5425,  24920000,  8892000,  311500, 111100, 42.6),
        new Profile("HEB 180", 180, 180, 8.5,14.0,  6525,  38310000, 13630000,  425700, 151400, 51.2),
        new Profile("HEB 200", 200, 200, 9.0,15.0,  7808,  56960000, 20030000,  569600, 200300, 61.3),
        new Profile("HEB 220", 220, 220, 9.5,16.0,  9104,  80910000, 28430000,  735600, 258500, 71.5),
        new Profile("HEB 240", 240, 240,10.0,17.0, 10599, 112600000, 39230000,  938300, 326900, 83.2),
        new Profile("HEB 260", 260, 260,10.0,17.5, 11843, 149200000, 51350000, 1148000, 395000, 93.0),
        new Profile("HEB 280", 280, 280,10.5,18.0, 13140, 192700000, 65950000, 1376000, 471000,103.0),
        new Profile("HEB 300", 300, 300,11.0,19.0, 14908, 251700000, 85630000, 1678000, 570900,117.0)
    );

    /** UPN — DIN 1026-1 (channel, subset). */
    public static final List<Profile> UPN = List.of(
        new Profile("UPN 80",  80,  45, 6.0, 8.0,  1100,   1060000,   194000,   26500,   8470,  8.64),
        new Profile("UPN 100",100,  50, 6.0, 8.5,  1350,   2060000,   293000,   41200,  11900, 10.6),
        new Profile("UPN 120",120,  55, 7.0, 9.0,  1700,   3640000,   432000,   60700,  16200, 13.4),
        new Profile("UPN 140",140,  60, 7.0,10.0,  2040,   6050000,   624000,   86400,  21500, 16.0),
        new Profile("UPN 160",160,  65, 7.5,10.5,  2400,   9250000,   853000,  116000,  27400, 18.8),
        new Profile("UPN 180",180,  70, 8.0,11.0,  2800,  13530000,  1140000,  150000,  34100, 22.0),
        new Profile("UPN 200",200,  75, 8.5,11.5,  3220,  19140000,  1480000,  191000,  41800, 25.3),
        new Profile("UPN 220",220,  80, 9.0,12.5,  3740,  26900000,  1970000,  245000,  51600, 29.4),
        new Profile("UPN 240",240,  85, 9.5,13.0,  4230,  35990000,  2480000,  300000,  61700, 33.2),
        new Profile("UPN 260",260,  90,10.0,14.0,  4830,  48190000,  3170000,  371000,  74700, 37.9),
        new Profile("UPN 300",300, 100,10.0,16.0,  5880,  80260000,  4950000,  535000, 113000, 46.2)
    );

    public record ProfileSeries(String name, List<Profile> profiles) {}

    public static final List<ProfileSeries> PROFILE_SERIES = List.of(
        new ProfileSeries("IPE", IPE),
        new ProfileSeries("HEA", HEA),
        new ProfileSeries("HEB", HEB),
        new ProfileSeries("UPN", UPN)
    );

    public static double convert(UnitCategory cat, UnitDef from, UnitDef to, double v) {
        if (cat.special() && "Temperatura".equals(cat.name())) {
            double k = switch (from.symbol()) {
                case "°C" -> v + 273.15;
                case "K"  -> v;
                case "°F" -> (v - 32.0) * 5.0/9.0 + 273.15;
                default   -> v;
            };
            return switch (to.symbol()) {
                case "°C" -> k - 273.15;
                case "K"  -> k;
                case "°F" -> (k - 273.15) * 9.0/5.0 + 32.0;
                default   -> k;
            };
        }
        return v * from.toBase() / to.toBase();
    }
}
