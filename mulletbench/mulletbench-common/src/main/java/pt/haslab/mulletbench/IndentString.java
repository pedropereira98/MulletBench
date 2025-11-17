package pt.haslab.mulletbench;

public class IndentString {

    public static String indent(int indentation){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indentation; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }
}
