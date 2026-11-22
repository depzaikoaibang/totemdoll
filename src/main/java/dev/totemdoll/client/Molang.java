package dev.totemdoll.client;

import java.util.HashMap;
import java.util.Map;

/** Tiny Molang evaluator: numbers, + - * /, comparisons, ?:, Math.sin/cos/..., q.* c.* v.* variables. */
public final class Molang {
    public interface Vars { double get(String name); }

    private final String src;
    private int pos;
    private Vars vars;
    private static final Map<String, Molang> CACHE = new HashMap<>();

    private Molang(String s) { this.src = s; }

    public static double eval(String expr, Vars vars) {
        try {
            Molang m = CACHE.computeIfAbsent(expr, Molang::new);
            m.pos = 0;
            m.vars = vars;
            double v = m.ternary();
            return v;
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    // ---- grammar
    private double ternary() {
        double c = or();
        ws();
        if (peek() == '?') {
            pos++;
            double a = ternary();
            ws();
            if (peek() == ':') pos++;
            double b = ternary();
            return c != 0 ? a : b;
        }
        return c;
    }

    private double or() {
        double v = and();
        while (true) { ws(); if (match("||")) { double r = and(); v = (v != 0 || r != 0) ? 1 : 0; } else return v; }
    }

    private double and() {
        double v = eq();
        while (true) { ws(); if (match("&&")) { double r = eq(); v = (v != 0 && r != 0) ? 1 : 0; } else return v; }
    }

    private double eq() {
        double v = cmp();
        while (true) {
            ws();
            if (match("==")) v = (v == cmp()) ? 1 : 0;
            else if (match("!=")) v = (v != cmp()) ? 1 : 0;
            else return v;
        }
    }

    private double cmp() {
        double v = add();
        while (true) {
            ws();
            if (match("<=")) v = v <= add() ? 1 : 0;
            else if (match(">=")) v = v >= add() ? 1 : 0;
            else if (match("<")) v = v < add() ? 1 : 0;
            else if (match(">")) v = v > add() ? 1 : 0;
            else return v;
        }
    }

    private double add() {
        double v = mul();
        while (true) {
            ws();
            char c = peek();
            if (c == '+') { pos++; v += mul(); }
            else if (c == '-') { pos++; v -= mul(); }
            else return v;
        }
    }

    private double mul() {
        double v = unary();
        while (true) {
            ws();
            char c = peek();
            if (c == '*') { pos++; v *= unary(); }
            else if (c == '/') { pos++; double d = unary(); v = d == 0 ? 0 : v / d; }
            else return v;
        }
    }

    private double unary() {
        ws();
        char c = peek();
        if (c == '-') { pos++; return -unary(); }
        if (c == '+') { pos++; return unary(); }
        if (c == '!') { pos++; return unary() == 0 ? 1 : 0; }
        return primary();
    }

    private double primary() {
        ws();
        char c = peek();
        if (c == '(') { pos++; double v = ternary(); ws(); if (peek() == ')') pos++; return v; }
        if (c == '\'') {
            int j = ++pos;
            while (pos < src.length() && src.charAt(pos) != '\'') pos++;
            String s = src.substring(j, pos);
            pos++;
            return s.hashCode();
        }
        if (Character.isDigit(c) || c == '.') {
            int j = pos;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
            return Double.parseDouble(src.substring(j, pos));
        }
        int j = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_' || src.charAt(pos) == '.')) pos++;
        String id = src.substring(j, pos).toLowerCase();
        if (id.isEmpty()) throw new IllegalStateException("bad token");
        ws();
        if (peek() == '(') {
            pos++;
            double[] a = new double[3];
            int n = 0;
            ws();
            if (peek() != ')') {
                while (true) {
                    double v = ternary();
                    if (n < 3) a[n++] = v;
                    ws();
                    if (peek() == ',') { pos++; continue; }
                    break;
                }
            }
            if (peek() == ')') pos++;
            return call(id, a, n);
        }
        if (id.equals("this")) return vars.get("this");
        if (id.equals("true")) return 1;
        if (id.equals("false")) return 0;
        return vars.get(id);
    }

    private static double call(String f, double[] a, int n) {
        switch (f) {
            case "math.sin": return Math.sin(Math.toRadians(a[0]));
            case "math.cos": return Math.cos(Math.toRadians(a[0]));
            case "math.abs": return Math.abs(a[0]);
            case "math.sqrt": return Math.sqrt(a[0]);
            case "math.min": return Math.min(a[0], a[1]);
            case "math.max": return Math.max(a[0], a[1]);
            case "math.pow": return Math.pow(a[0], a[1]);
            case "math.floor": return Math.floor(a[0]);
            case "math.clamp": return Math.max(a[1], Math.min(a[2], a[0]));
            case "math.lerp": return a[0] + (a[1] - a[0]) * a[2];
            default: return 0;
        }
    }

    private void ws() { while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++; }
    private char peek() { return pos < src.length() ? src.charAt(pos) : '\0'; }
    private boolean match(String s) {
        if (src.startsWith(s, pos)) { pos += s.length(); return true; }
        return false;
    }
}
