package us.codecraft.xsoup.xevaluator;

import org.jsoup.helper.Validate;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operate on element to get XPath result.
 *
 * @author code4crafter@gmail.com
 */
public abstract class ElementOperator {

    public abstract String operate(Element element);

    public static class AttributeGetter extends ElementOperator {

        private String attribute;

        public AttributeGetter(String attribute) {
            this.attribute = attribute;
        }

        @Override
        public String operate(Element element) {
            return element.attr(attribute);
        }

        @Override
        public String toString() {
            return "@" + attribute;
        }
    }

    public static class AllText extends ElementOperator {

        @Override
        public String operate(Element element) {
            return element.text();
        }

        @Override
        public String toString() {
            return "allText()";
        }
    }

    public static class Html extends ElementOperator {

        @Override
        public String operate(Element element) {
            return element.html();
        }

        @Override
        public String toString() {
            return "html()";
        }
    }

    public static class OuterHtml extends ElementOperator {

        @Override
        public String operate(Element element) {
            return element.outerHtml();
        }

        @Override
        public String toString() {
            return "outerHtml()";
        }
    }

    public static class TidyText extends ElementOperator {

        @Override
        public String operate(Element element) {

            FormattingVisitor formatter = new FormattingVisitor();
            NodeTraversor traversor = new NodeTraversor(formatter);
            traversor.traverse(element); // walk the DOM, and call .head() and .tail() for each node

            return formatter.toString();
        }

        @Override
        public String toString() {
            return "tidyText()";
        }
    }

    public static class GroupedText extends ElementOperator {

        private int group;

        public GroupedText(int group) {
            this.group = group;
        }

        @Override
        public String operate(Element element) {
            int index = 0;
            StringBuilder accum = new StringBuilder();
            for (Node node : element.childNodes()) {
                if (node instanceof TextNode) {
                    TextNode textNode = (TextNode) node;
                    if (group == 0) {
                        accum.append(textNode.text());
                    } else if (++index == group) {
                        return textNode.text();
                    }
                }
            }
            return accum.toString();
        }

        @Override
        public String toString() {
            return String.format("text(%d)", group);
        }
    }

    /**
     * usage:
     * <br>
     * regex('.*')
     * regex(@attr,'.*')
     * regex(@attr,'.*',group)
     */
    public static class Regex extends ElementOperator {

        private Pattern pattern;

        private String attribute;

        private int group;

        public Regex(String expr) {
            this.pattern = Pattern.compile(expr);
        }

        public Regex(String expr, String attribute) {
            this.attribute = attribute;
            this.pattern = Pattern.compile(expr);
        }

        public Regex(String expr, String attribute, int group) {
            this.attribute = attribute;
            this.pattern = Pattern.compile(expr);
            this.group = group;
        }

        @Override
        public String operate(Element element) {
            Matcher matcher = pattern.matcher(getSource(element));
            if (matcher.find()) {
                return matcher.group(group);
            }
            return null;
        }

        protected String getSource(Element element) {
            if (attribute == null) {
                return element.outerHtml();
            } else {
                String attr = element.attr(attribute);
                Validate.notNull(attr, "Attribute " + attribute + " of " + element + " is not exist!");
                return attr;
            }
        }

        @Override
        public String toString() {
            return String.format("regex(%s%s%s)",
                    attribute != null ? "@" + attribute + "," : "", pattern.toString(),
                    group != 0 ? "," + group : "");
        }
    }

    private class FormattingVisitor implements NodeVisitor {
        private static final int maxWidth = 80;
        private int width = 0;
        private StringBuilder accum = new StringBuilder(); // holds the accumulated text

        // hit when the node is first seen
        public void head(Node node, int depth) {
            String name = node.nodeName();
            if (node instanceof TextNode)
                append(((TextNode) node).text()); // TextNodes carry all user-readable text in the DOM.
            else if (name.equals("li"))
                append("\n * ");
            else if (name.equals("dt"))
                append("  ");
            else if (StringUtil.in(name, "p", "h1", "h2", "h3", "h4", "h5", "tr"))
                append("\n");
        }

        // hit when all of the node's children (if any) have been visited
        public void tail(Node node, int depth) {
            String name = node.nodeName();
            if (StringUtil.in(name, "br", "dd", "dt", "p", "h1", "h2", "h3", "h4", "h5"))
                append("\n");
            else if (name.equals("a"))
                append(String.format(" <%s>", node.absUrl("href")));
        }

        // appends text to the string builder with a simple word wrap method
        private void append(String text) {
            if (text.startsWith("\n"))
                width = 0; // reset counter if starts with a newline. only from formats above, not in natural text
            if (text.equals(" ") &&
                    (accum.length() == 0 || StringUtil.in(accum.substring(accum.length() - 1), " ", "\n")))
                return; // don't accumulate long runs of empty spaces

            if (text.length() + width > maxWidth) { // won't fit, needs to wrap
                String words[] = text.split("\\s+");
                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    boolean last = i == words.length - 1;
                    if (!last) // insert a space if not the last word
                        word = word + " ";
                    if (word.length() + width > maxWidth) { // wrap and reset counter
                        accum.append("\n").append(word);
                        width = word.length();
                    } else {
                        accum.append(word);
                        width += word.length();
                    }
                }
            } else { // fits as is, without need to wrap text
                accum.append(text);
                width += text.length();
            }
        }

        @Override
        public String toString() {
            return accum.toString();
        }
    }
}
