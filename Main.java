import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;

public class Main extends JFrame {
    private JLabel inputLabel, outputLabel;
    private JTextArea tablaSimpleArea, tablaDetalladaArea, analisisArea, latexArea;
    private JTabbedPane pestañas;

    public Main() {
        initComponents();
    }

    // ==================== PARSER Y EVALUADOR ====================
    private List<String> obtenerPostfijo(String expresion) {
        Map<String, Integer> pri = Map.of(
            "¬", 4, "∧", 3, "∨", 2, "→", 1, "↔", 0
        );
        List<String> tokens = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (char c : expresion.toCharArray()) {
            if (esOperador(c) || c == '(' || c == ')') {
                if (buf.length() > 0) { tokens.add(buf.toString()); buf.setLength(0); }
                tokens.add(String.valueOf(c));
            } else buf.append(c);
        }
        if (buf.length() > 0) tokens.add(buf.toString());

        Stack<String> ops = new Stack<>();
        List<String> salida = new ArrayList<>();
        for (String t : tokens) {
            if (t.matches("[PQRS]")) salida.add(t);
            else if (pri.containsKey(t)) {
                while (!ops.isEmpty() && !ops.peek().equals("(") && pri.get(ops.peek()) >= pri.get(t)) {
                    salida.add(ops.pop());
                }
                ops.push(t);
            } else if (t.equals("(")) {
                ops.push(t);
            } else if (t.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) salida.add(ops.pop());
                ops.pop();
            }
        }
        while (!ops.isEmpty()) salida.add(ops.pop());
        return salida;
    }

    private boolean evaluarPostfijo(List<String> post, Map<Character, Boolean> vals) {
        Stack<Boolean> pila = new Stack<>();
        for (String t : post) {
            if (t.matches("[PQRS]")) pila.push(vals.get(t.charAt(0)));
            else if (t.equals("¬")) pila.push(!pila.pop());
            else {
                boolean b = pila.pop(), a = pila.pop();
                switch (t) {
                    case "∧": pila.push(a && b); break;
                    case "∨": pila.push(a || b); break;
                    case "→": pila.push(!a || b); break;
                    case "↔": pila.push(a == b); break;
                }
            }
        }
        return pila.pop();
    }

    private void validarExpresion(String exp) {
        if (exp.isEmpty()) throw new IllegalArgumentException("Expresión vacía");
        int balance = 0;
        for (int i = 0; i < exp.length(); i++) {
            char c = exp.charAt(i);
            if (!esVariable(c) && !esOperador(c) && c!='(' && c!=')')
                throw new IllegalArgumentException("Carácter inválido: " + c);
            if (c=='(') balance++;
            if (c==')') balance--;
            if (balance < 0) throw new IllegalArgumentException("Paréntesis desbalanceados");
        }
        if (balance != 0) throw new IllegalArgumentException("Paréntesis desbalanceados");
    }

    private List<Character> extraerVariables(String exp) {
        Set<Character> set = new HashSet<>();
        for (char c : exp.toCharArray()) if (esVariable(c)) set.add(c);
        List<Character> vars = new ArrayList<>(set);
        Collections.sort(vars);
        return vars;
    }

    private boolean esVariable(char c) { return "PQRS".indexOf(c) >= 0; }
    private boolean esOperador(char c) { return "¬∧∨→↔".indexOf(c) >= 0; }
    private boolean esOperador(String s) { return s.length()==1 && esOperador(s.charAt(0)); }

    // ==================== GENERACIÓN DE SUBFÓRMULAS ====================
    private List<String> generarPasos(String expresion) {
        List<String> post = obtenerPostfijo(expresion);
        Stack<String> p = new Stack<>();
        List<String> pasos = new ArrayList<>();
        for (String t : post) {
            if (esOperador(t)) {
                if (t.equals("¬")) {
                    String a = p.pop();
                    String sub = "(" + t + a + ")";
                    pasos.add(sub);
                    p.push(sub);
                } else {
                    String b = p.pop(), a = p.pop();
                    String sub = "(" + a + t + b + ")";
                    pasos.add(sub);
                    p.push(sub);
                }
            } else {
                p.push(t);
            }
        }
        return pasos;
    }

    // ==================== TABLAS DE VERDAD ====================
    private String generarTablaSimple(List<String> post, List<Character> vars) {
        StringBuilder sb = new StringBuilder();
        vars.forEach(v -> sb.append(v).append("\t")); sb.append("| R\n");
        int filas = 1 << vars.size();
        for (int i = 0; i < filas; i++) {
            Map<Character, Boolean> asign = new LinkedHashMap<>();
            for (int j = 0; j < vars.size(); j++) {
                boolean val = ((i >> (vars.size()-j-1)) & 1) == 1;
                asign.put(vars.get(j), val);
                sb.append(val?"V":"F").append("\t");
            }
            boolean r = evaluarPostfijo(post, asign);
            sb.append("| ").append(r?"V":"F").append("\n");
        }
        return sb.toString();
    }

    private String generarTablaDetallada(String exp, List<String> post, List<Character> vars) {
        StringBuilder sb = new StringBuilder();
        List<String> pasos = generarPasos(exp);
        vars.forEach(v -> sb.append(v).append("\t"));
        pasos.forEach(sub -> sb.append(sub).append("\t"));
        sb.append("| R\n");
        int filas = 1 << vars.size();
        for (int i = 0; i < filas; i++) {
            Map<Character, Boolean> asign = new LinkedHashMap<>();
            for (int j = 0; j < vars.size(); j++) {
                boolean val = ((i >> (vars.size()-j-1)) & 1) == 1;
                asign.put(vars.get(j), val);
                sb.append(val?"V":"F").append("\t");
            }
            Map<String, Boolean> valoresSub = new LinkedHashMap<>();
            for (String sub : pasos) {
                boolean v = evaluarPostfijo(obtenerPostfijo(sub), asign);
                valoresSub.put(sub, v);
                sb.append(v?"V":"F").append("\t");
            }
            boolean r = valoresSub.get(pasos.get(pasos.size()-1));
            sb.append("| ").append(r?"V":"F").append("\n");
        }
        return sb.toString();
    }

    // ==================== LATEX ====================
    private String convertirALaTeX(String exp) {
        return exp.replace("¬", "\\neg ")
                  .replace("∧", "\\land ")
                  .replace("∨", "\\lor ")
                  .replace("→", "\\to ")
                  .replace("↔", "\\leftrightarrow ");
    }

    private String generarLaTeXTablaDetallada(String exp, List<Character> vars) {
        List<String> pasos = generarPasos(exp);
        StringBuilder sb = new StringBuilder();
        sb.append("\\begin{tabular}{");
        vars.forEach(v -> sb.append("c "));
        pasos.forEach(sub -> sb.append("c "));
        sb.append("| c}");
        // encabezados
        for (char v : vars) sb.append(v).append(" & ");
        for (String sub : pasos) sb.append("\\( ").append(convertirALaTeX(sub)).append("\\) & ");
        sb.append("R \\ \\ \\hline\n");
        int filas = 1 << vars.size();
        for (int i = 0; i < filas; i++) {
            Map<Character, Boolean> asign = new LinkedHashMap<>();
            for (int j = 0; j < vars.size(); j++) {
                boolean val = ((i >> (vars.size()-j-1)) & 1) == 1;
                asign.put(vars.get(j), val);
                sb.append(val?"T":"F").append(" & ");
            }
            Map<String, Boolean> valoresSub = new LinkedHashMap<>();
            for (String sub : pasos) {
                boolean v = evaluarPostfijo(obtenerPostfijo(sub), asign);
                sb.append(v?"T":"F").append(" & ");
            }
            boolean r = evaluarPostfijo(obtenerPostfijo(exp), asign);
            sb.append(r?"T":"F").append(" \\\\ \n");
        }
        sb.append("\\end{tabular}");
        return sb.toString();
    }

    private void exportarALaConsolaYArchivo(String exp, String tablaDet, String latexTabDet, String latexProp, String analisis) {
        System.out.println("Fórmula: " + exp);
        System.out.println("--- Análisis de pasos ---\n" + analisis);
        System.out.println("--- Tabla Detallada ---\n" + tablaDet);
        System.out.println("--- LaTeX Proposición ---\n" + latexProp);
        System.out.println("--- LaTeX Tabla Detallada ---\n" + latexTabDet);

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String nombre = "export_" + ts + ".txt";
        try (FileWriter fw = new FileWriter(nombre)) {
            fw.write("Proposición LaTeX:\n" + latexProp + "\n\n");
            fw.write("Análisis de pasos:\n" + analisis + "\n\n");
            fw.write("Tabla Detallada LaTeX:\n" + latexTabDet + "\n");
            fw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        outputLabel.setText("Procesado y exportado: " + nombre);
    }

    private void procesarFormula() {
        String exp = inputLabel.getText().trim();
        try {
            validarExpresion(exp);
            List<String> post = obtenerPostfijo(exp);
            List<Character> vars = extraerVariables(exp);

            String tablaSimple = generarTablaSimple(post, vars);
            tablaSimpleArea.setText(tablaSimple);

            String tablaDet = generarTablaDetallada(exp, post, vars);
            tablaDetalladaArea.setText(tablaDet);

            String analisis = String.join("\n", generarPasos(exp));
            analisisArea.setText(analisis);

            String latexProp = "\\(" + convertirALaTeX(exp) + "\\)";
            String latexTab = generarLaTeXTablaDetallada(exp, vars);
            latexArea.setText(latexProp + "\n\n" + latexTab);

            exportarALaConsolaYArchivo(exp, tablaDet, latexTab, latexProp, analisis);
        } catch (IllegalArgumentException ex) {
            outputLabel.setText("Error: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Lógica Proposicional - Detalle y Exportación");
        setResizable(false);

        inputLabel = new JLabel("", SwingConstants.RIGHT);
        outputLabel = new JLabel("", SwingConstants.LEFT);
        inputLabel.setOpaque(true); inputLabel.setBackground(java.awt.Color.WHITE);
        outputLabel.setOpaque(true); outputLabel.setBackground(java.awt.Color.WHITE);

        tablaSimpleArea    = new JTextArea(8, 30);
        tablaDetalladaArea = new JTextArea(8, 30);
        analisisArea       = new JTextArea(8, 30);
        latexArea          = new JTextArea(8, 30);
        for (JTextArea ta : List.of(tablaSimpleArea, tablaDetalladaArea, analisisArea, latexArea)) ta.setEditable(false);

        pestañas = new JTabbedPane();
        pestañas.addTab("Tabla Simple",    new JScrollPane(tablaSimpleArea));
        pestañas.addTab("Tabla Detallada", new JScrollPane(tablaDetalladaArea));
        pestañas.addTab("Análisis",       new JScrollPane(analisisArea));
        pestañas.addTab("LaTeX",          new JScrollPane(latexArea));

        JPanel pb = new JPanel(new java.awt.GridLayout(5, 4, 5, 5));
        String[] bt = {"AC","DEL","(",")","P","Q","R","S","¬","∧","∨","→","↔","="};
        for (String t : bt) {
            JButton b = new JButton(t);
            b.addActionListener(e -> {
                switch (t) {
                    case "AC": inputLabel.setText(""); break;
                    case "DEL":
                        String s = inputLabel.getText();
                        if (!s.isEmpty()) inputLabel.setText(s.substring(0, s.length()-1));
                        break;
                    case "=": procesarFormula(); break;
                    default: inputLabel.setText(inputLabel.getText() + t.trim());
                }
            });
            pb.add(b);
        }

        Box main = Box.createVerticalBox();
        main.add(inputLabel);
        main.add(Box.createVerticalStrut(5));
        main.add(outputLabel);
        main.add(Box.createVerticalStrut(5));
        main.add(pb);
        main.add(Box.createVerticalStrut(5));
        main.add(pestañas);

        add(main);
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}