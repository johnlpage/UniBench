package com.mongodb.solcon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonFormulaPreprocessor {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  // Pattern to find formulas - supports both with and without operations
  // Matches: =$var or =$var*123 or =$var.nested/2 etc.
  private static final Pattern EMBEDDED_FORMULA_PATTERN =
      Pattern.compile("=\\$([a-zA-Z0-9_.]+)(?:\\s*([*/+\\-])\\s*([0-9.]+))?");

  /**
   * Preprocess a JSON string, replacing formula expressions with their evaluated values. Formulas
   * can be: - "=$variableName" (just the variable value) - "=$variableName*1.23" (variable with
   * operation) Can be standalone or embedded in strings like "RANDINT(1,=$x)" All formula results
   * are converted to integers.
   *
   * @param jsonString The input JSON string
   * @return A JSON string with formulas replaced by their integer values
   */
  public static String preprocessJson(String jsonString) throws Exception {
    JsonNode rootNode = objectMapper.readTree(jsonString);
    JsonNode processedNode = processNode(rootNode, rootNode);
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(processedNode);
  }

  /** Recursively process nodes in the JSON structure */
  private static JsonNode processNode(JsonNode node, JsonNode rootNode) {
    if (node.isObject()) {
      ObjectNode objectNode = objectMapper.createObjectNode();
      node.fields()
          .forEachRemaining(
              entry -> {
                JsonNode processedValue = processNode(entry.getValue(), rootNode);
                objectNode.set(entry.getKey(), processedValue);
              });
      return objectNode;
    } else if (node.isArray()) {
      ArrayNode arrayNode = objectMapper.createArrayNode();
      node.forEach(item -> arrayNode.add(processNode(item, rootNode)));
      return arrayNode;
    } else if (node.isTextual()) {
      String text = node.asText();
      return processTextNode(text, rootNode);
    }
    return node;
  }

  /** Process a text node, handling both standalone formulas and embedded formulas */
  private static JsonNode processTextNode(String text, JsonNode rootNode) {
    // Check if there are any formulas in the string
    if (!text.contains("=$")) {
      return TextNode.valueOf(text);
    }

    Matcher matcher = EMBEDDED_FORMULA_PATTERN.matcher(text);
    StringBuffer result = new StringBuffer();
    boolean foundFormula = false;

    while (matcher.find()) {
      foundFormula = true;
      try {
        String jsonPath = matcher.group(1);
        String operator = matcher.group(2); // May be null
        String operandStr = matcher.group(3); // May be null

        long value = evaluateFormula(jsonPath, operator, operandStr, rootNode);

        // Always format as integer
        matcher.appendReplacement(result, String.valueOf(value));
      } catch (Exception e) {
        System.err.println(
            "Warning: Could not evaluate embedded formula '"
                + matcher.group(0)
                + "': "
                + e.getMessage());
        // Keep the original text if evaluation fails
        matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
      }
    }
    matcher.appendTail(result);

    if (foundFormula) {
      String processedText = result.toString();

      // Check if the entire result is just a formula (no other text)
      // If the original text started with "=" and had nothing else, convert to number
      if (text.trim().startsWith("=")
          && !text.contains(" ")
          && text.indexOf("=") == text.lastIndexOf("=")) {
        try {
          long numValue = Long.parseLong(processedText);
          return LongNode.valueOf(numValue);
        } catch (NumberFormatException e) {
          // Not a pure number, keep as string
        }
      }

      return TextNode.valueOf(processedText);
    }

    // No formulas found, return original
    return TextNode.valueOf(text);
  }

  /**
   * Evaluate a formula and return as integer
   *
   * @param jsonPath The JSON path (e.g., "totalDocsToInsert" or "nested.originalDocs")
   * @param operator The operator (can be null if just a variable reference)
   * @param operandStr The operand as string (can be null if just a variable reference)
   * @param rootNode The root JSON node
   * @return The integer result of the formula evaluation (rounded if necessary)
   */
  private static long evaluateFormula(
      String jsonPath, String operator, String operandStr, JsonNode rootNode) throws Exception {
    // Navigate the JSON path
    double value = getValueAtPath(rootNode, jsonPath);

    // If no operator, just return the value as integer
    if (operator == null || operandStr == null) {
      return Math.round(value);
    }

    double operand = Double.parseDouble(operandStr);
    double result;

    // Perform the operation
    switch (operator) {
      case "*":
        result = value * operand;
        break;
      case "/":
        result = value / operand;
        break;
      case "+":
        result = value + operand;
        break;
      case "-":
        result = value - operand;
        break;
      default:
        throw new IllegalArgumentException("Unsupported operator: " + operator);
    }

    // Always return as integer (rounded)
    return Math.round(result);
  }

  /**
   * Get a numeric value at a JSON path (supports dot notation)
   *
   * @param rootNode The root JSON node
   * @param path The path (e.g., "nested.originalDocs")
   * @return The numeric value at that path
   */
  private static double getValueAtPath(JsonNode rootNode, String path) throws Exception {
    String[] pathParts = path.split("\\.");
    JsonNode currentNode = rootNode;

    for (String part : pathParts) {
      if (currentNode.has(part)) {
        currentNode = currentNode.get(part);
      } else {
        throw new IllegalArgumentException("JSON path '" + path + "' not found");
      }
    }

    if (currentNode.isNumber()) {
      return currentNode.asDouble();
    } else {
      throw new IllegalArgumentException("Value at '" + path + "' is not numeric: " + currentNode);
    }
  }

  /** Example usage */
  public static void main(String[] args) {
    String testJson =
        """
        {
          "initialDocsToInsert": 6291456,
          "totalDocsToInsert": 1000000,
          "group": "RANDINT(1,12500)",
          "_id": "RANDINT(1,=$initialDocsToInsert)",
          "_id2": "RANDINT(1,=$initialDocsToInsert/500)",
          "complexId": "RANDINT(=$totalDocsToInsert*0.5,=$initialDocsToInsert)",
          "justVariable": "=$totalDocsToInsert",
          "scaledValue": "=$totalDocsToInsert*1.23",
          "halfValue": "=$totalDocsToInsert/2",
          "divisionWithRemainder": "=$totalDocsToInsert/3",
          "embeddedInString": "The value is =$totalDocsToInsert documents and half is =$totalDocsToInsert/2",
          "multipleFormulas": "Range(=$totalDocsToInsert,=$initialDocsToInsert)",
          "nested": {
            "originalDocs": 1000,
            "doubledDocs": "=$nested.originalDocs*2",
            "sameValue": "=$nested.originalDocs",
            "thirdValue": "=$nested.originalDocs/3",
            "description": "We have =$nested.originalDocs total items"
          },
          "regularString": "This stays as is",
          "array": [
            "=$totalDocsToInsert",
            "=$totalDocsToInsert*0.5",
            "=$totalDocsToInsert*1.234",
            "FUNC(=$totalDocsToInsert,=$initialDocsToInsert/2)",
            "normal string"
          ]
        }
        """;

    try {
      System.out.println("Original JSON:");
      System.out.println(testJson);
      System.out.println("\n" + "=".repeat(50) + "\n");

      String result = preprocessJson(testJson);
      System.out.println("Processed JSON:");
      System.out.println(result);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
