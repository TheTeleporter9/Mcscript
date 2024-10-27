import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CustomLangToDatapackCompiler {

    private static final String NAMESPACE = "mynamespace"; // Namespace for the datapack

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Prompt user for the path to the custom .mc file
        System.out.print("Enter the path to the .mc file: ");
        String sourceFilePath = scanner.nextLine();

        // Prompt user for the export path of the Minecraft datapack
        System.out.print("Enter the export path for the Minecraft datapack: ");
        String datapackFolderPath = scanner.nextLine();

        try {
            compileToDatapack(sourceFilePath, datapackFolderPath);
            System.out.println("Datapack created successfully at: " + datapackFolderPath);
        } catch (IOException e) {
            System.err.println("Error creating datapack: " + e.getMessage());
        }
    }

    public static void compileToDatapack(String sourceFilePath, String datapackFolderPath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(sourceFilePath));
        Map<String, List<String>> functions = parseCustomLanguage(lines);

        createDatapackStructure(datapackFolderPath);
        writeFunctionsToDatapack(datapackFolderPath, functions);
        writePackMeta(datapackFolderPath);
    }

    private static Map<String, List<String>> parseCustomLanguage(List<String> lines) {
        Map<String, List<String>> functions = new HashMap<>();
        List<String> currentFunctionCommands = null;
        String currentFunctionName = null;

        Pattern functionPattern = Pattern.compile("^function\\s+(\\w+)\\s*\\(.*=>\\s*(\\w+)\\)");
        Pattern varPattern = Pattern.compile("^var\\s+(\\w+)\\s*=\\s*(.*)");
        Pattern sayPattern = Pattern.compile("^\\s*say\\s+\"(.*)\"\\s*$");
        Pattern executePattern = Pattern.compile("^\\s*execute\\s*\\(=>\\{(.*)\\},\\s*\\{(.*)\\}\\)");
        // Pattern to match other Minecraft commands
        Pattern otherCommandPattern = Pattern.compile("^\\s*(\\w+)\\s+(.*)$");

        for (String line : lines) {
            line = line.trim();

            // Check if the line is a function definition
            Matcher functionMatcher = functionPattern.matcher(line);
            if (functionMatcher.matches()) {
                // Save the previous function, if any
                if (currentFunctionName != null && currentFunctionCommands != null) {
                    functions.put(currentFunctionName, currentFunctionCommands);
                }

                // Start a new function block
                currentFunctionName = functionMatcher.group(1);
                currentFunctionCommands = new ArrayList<>();
                continue;
            }

            // Ensure we're inside a function block
            if (currentFunctionCommands != null) {
                // Check if the line is a variable definition
                Matcher varMatcher = varPattern.matcher(line);
                if (varMatcher.matches()) {
                    String varName = varMatcher.group(1);
                    String varValue = varMatcher.group(2);
                    currentFunctionCommands.add("scoreboard objectives add " + varName + " dummy");
                    continue;
                }

                // Check if the line is a say command
                Matcher sayMatcher = sayPattern.matcher(line);
                if (sayMatcher.matches()) {
                    String message = sayMatcher.group(1);
                    currentFunctionCommands.add("say " + message);
                    continue;
                }

                // Check if the line is an execute command
                Matcher executeMatcher = executePattern.matcher(line);
                if (executeMatcher.matches()) {
                    String executeConditions = executeMatcher.group(1).trim();
                    String action = executeMatcher.group(2).trim();

                    // Format the execute command properly
                    String executeCommand = "execute " + formatExecuteConditions(executeConditions) + " run " + action;
                    currentFunctionCommands.add(executeCommand);
                    continue;
                }

                // Check if the line is another Minecraft command
                Matcher otherCommandMatcher = otherCommandPattern.matcher(line);
                if (otherCommandMatcher.matches()) {
                    String commandName = otherCommandMatcher.group(1);
                    String commandArgs = otherCommandMatcher.group(2);
                    currentFunctionCommands.add(commandName + " " + commandArgs);
                    continue;
                }
            }

            // Check for end of function
            if (line.equals("end")) {
                if (currentFunctionName != null && currentFunctionCommands != null) {
                    functions.put(currentFunctionName, currentFunctionCommands);
                }
                currentFunctionName = null;
                currentFunctionCommands = null;
            }
        }

        // Add the last function if any
        if (currentFunctionName != null && currentFunctionCommands != null) {
            functions.put(currentFunctionName, currentFunctionCommands);
        }

        return functions;
    }

    private static String formatExecuteConditions(String conditions) {
        // Example: "as Player.all at current" should be formatted to "as @a"
        conditions = conditions.replace("Player.all", "@a");
        // More replacements can be added based on your custom conditions
        return conditions.replace(" at current", ""); // Remove 'at current' for simplicity
    }

    private static void createDatapackStructure(String datapackFolderPath) throws IOException {
        Path basePath = Paths.get(datapackFolderPath);
        Path dataPath = Paths.get(datapackFolderPath, "data", NAMESPACE);
        Path functionsPath = Paths.get(datapackFolderPath, "data", NAMESPACE, "functions");
        Path tagsPath = Paths.get(datapackFolderPath, "data", NAMESPACE, "tags", "functions");

        // Create the necessary directories
        Files.createDirectories(functionsPath);
        Files.createDirectories(tagsPath);
    }

    private static void writeFunctionsToDatapack(String datapackFolderPath, Map<String, List<String>> functions) throws IOException {
        for (Map.Entry<String, List<String>> entry : functions.entrySet()) {
            String functionName = entry.getKey();
            List<String> commands = entry.getValue();

            // Write to the .mcfunction file
            Path functionFilePath = Paths.get(datapackFolderPath, "data", NAMESPACE, "functions", functionName + ".mcfunction");
            Files.write(functionFilePath, commands);
        }

        // Create a 'load' function to automatically run when the datapack is loaded
        if (functions.containsKey("load")) {
            String loadFunction = "say \"Loading datapack...\""; // Optional message on load
            Path loadFilePath = Paths.get(datapackFolderPath, "data", NAMESPACE, "functions", "load.mcfunction");
            Files.write(loadFilePath, Collections.singleton(loadFunction));
        }

        // Write the tags for load and update functions
        writeTagFile(datapackFolderPath, "load", "load");
        writeTagFile(datapackFolderPath, "update", "update");
    }

    private static void writeTagFile(String datapackFolderPath, String tagName, String functionName) throws IOException {
        String tagContent = "{ \"values\": [\"" + NAMESPACE + ":" + functionName + "\"] }";
        Path tagFilePath = Paths.get(datapackFolderPath, "data", NAMESPACE, "tags", "functions", tagName + ".json");
        Files.write(tagFilePath, Collections.singleton(tagContent));
    }

    private static void writePackMeta(String datapackFolderPath) throws IOException {
        String packMetaContent = """
        {
          "pack": {
            "pack_format": 10,
            "description": "Custom .mc Language Datapack"
          }
        }
        """;
        Path packMetaPath = Paths.get(datapackFolderPath, "pack.mcmeta");
        Files.writeString(packMetaPath, packMetaContent);
    }
}
