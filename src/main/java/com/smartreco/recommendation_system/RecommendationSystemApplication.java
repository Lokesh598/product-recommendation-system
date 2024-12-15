package com.smartreco.recommendation_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.FileReader;
import java.util.*;

import com.opencsv.CSVReader;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

@SpringBootApplication
public class RecommendationSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecommendationSystemApplication.class, args);
	}

}

class ProductRecommendation {

	// Graph to represent user-product relationships
	private static Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
	private static Map<String, Map<String, String>> productAttributes = new HashMap<>();
	private static Map<String, Integer> productPopularity = new HashMap<>();

	public static void main(String[] args) throws Exception {
		// Load CSV data
		String userActivityCsv = "C:\\Users\\lsingh\\dev-pro\\learnings\\recommendation-system\\src\\main\\resources\\user_activity.csv"; // File path
		String productCsv = "C:\\Users\\lsingh\\dev-pro\\learnings\\recommendation-system\\src\\main\\resources\\product.csv"; // File path

		loadUserActivity(userActivityCsv);
		loadProducts(productCsv);
		loadProductAttributes(productCsv);

		// Generate recommendations for a specific user
		String userId = "1"; // Example user ID
		List<String> recommendations = recommendProducts(userId);
		System.out.println("Recommendations for User " + userId + ": " + recommendations);

		List<String> contentBasedRecommendations = recommendProductsByContent("1");
		System.out.println("Content-Based Recommendations for User 1: " + contentBasedRecommendations);

		List<String> popularityBasedRecommendations = recommendProductsByPopularity();
		System.out.println("Popularity-Based Recommendations: " + popularityBasedRecommendations);
	}

	/**
	 * Load user activity data
	 * map user to product and product to user in graph and also load product popularity
	 * @param filePath
	 * @throws Exception
	 */
	private static void loadUserActivity(String filePath) throws Exception {
		try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
			String[] line;
			reader.readNext(); // Skip header
			while ((line = reader.readNext()) != null) {
				String userId = "user_" + line[0];
				String productId = "product_" + line[1];

				// Add nodes and edges to the graph
				graph.addVertex(userId);
				graph.addVertex(productId);
				graph.addEdge(userId, productId);

				// load product popularity
				productPopularity.put(productId, productPopularity.getOrDefault(productId, 0) + 1);
			}
		}
	}

	// Load product data (optional, for displaying product details)
	private static Map<String, String> loadProducts(String filePath) throws Exception {
		Map<String, String> productDetails = new HashMap<>();
		try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
			String[] line;
			reader.readNext(); // Skip header
			while ((line = reader.readNext()) != null) {
				String productId = "product_" + line[0];
				String productName = line[3]; // Assuming name is the 4th column
				productDetails.put(productId, productName);
			}
		}
		return productDetails;
	}

	private static void loadProductAttributes(String filePath) throws Exception {
		try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
			String[] line;
			reader.readNext(); // Skip header
			while ((line = reader.readNext()) != null) {
				String productId = "product_" + line[0];
				Map<String, String> attributes = new HashMap<>();
				attributes.put("category", line[1]);
				attributes.put("price_range", line[2]);
				attributes.put("brand", line[3]);
				productAttributes.put(productId, attributes);
			}
		}
	}

	private static List<String> recommendProducts(String userId) {
		String graphUserId = "user_" + userId; // Prefix user ID to match graph nodes
		Set<String> visitedProducts = new HashSet<>();
		List<String> recommendedProducts = new ArrayList<>();

		// Check if the user exists in the graph
		if (!graph.containsVertex(graphUserId)) {
			throw new IllegalArgumentException("User not found in graph: " + userId);
		}

		// Find all products directly connected to the user
		for (DefaultEdge edge : graph.edgesOf(graphUserId)) {
			String connectedNode = graph.getEdgeTarget(edge).equals(graphUserId)
					? graph.getEdgeSource(edge)
					: graph.getEdgeTarget(edge);
			if (connectedNode.startsWith("product_")) {
				visitedProducts.add(connectedNode);
			}
		}

		for (String product : visitedProducts) {
			for (DefaultEdge edge : graph.edgesOf(product)) {
				String connectedNode = graph.getEdgeTarget(edge).equals(product)
						? graph.getEdgeSource(edge)
						: graph.getEdgeTarget(edge);

				// If the connected node is another user, check their products
				if (connectedNode.startsWith("user_") && !connectedNode.equals(graphUserId)) {
					for (DefaultEdge userEdge : graph.edgesOf(connectedNode)) {
						String otherProduct = graph.getEdgeTarget(userEdge).equals(connectedNode)
								? graph.getEdgeSource(userEdge)
								: graph.getEdgeTarget(userEdge);

						// Add products that the target user hasn't interacted with
						if (otherProduct.startsWith("product_") && !visitedProducts.contains(otherProduct)) {
							recommendedProducts.add(otherProduct);
						}
					}
				}
			}
		}


		// Remove duplicates and limit to top N recommendations
		return new ArrayList<>(new HashSet<>(recommendedProducts));
	}

	private static List<String> recommendProductsByContent(String userId) {
		String graphUserId = "user_" + userId;
		if (!graph.containsVertex(graphUserId)) {
			throw new IllegalArgumentException("User not found in graph: " + userId);
		}

		Set<String> userProducts = new HashSet<>();
		for (DefaultEdge edge : graph.edgesOf(graphUserId)) {
			String connectedNode = graph.getEdgeTarget(edge).equals(graphUserId)
					? graph.getEdgeSource(edge)
					: graph.getEdgeTarget(edge);
			if (connectedNode.startsWith("product_")) {
				userProducts.add(connectedNode);
			}
		}

		Set<String> recommendedProducts = new HashSet<>();
		for (String product : userProducts) {
			Map<String, String> productAttr = productAttributes.get(product);
			if (productAttr == null) continue;

			for (Map.Entry<String, Map<String, String>> entry : productAttributes.entrySet()) {
				String otherProduct = entry.getKey();
				if (userProducts.contains(otherProduct)) continue; // Skip products the user already interacted with
				Map<String, String> otherAttr = entry.getValue();

				// Recommend products with the same category
				if (productAttr.get("category").equals(otherAttr.get("category"))) {
					recommendedProducts.add(otherProduct);
				}
			}
		}

		return new ArrayList<>(recommendedProducts);
	}

	/**
	 * Recommend products based on popularity or cold start
	 * cold start: recommend top 5 products
	 * @return
	 */
	private static List<String> recommendProductsByPopularity() {
		return productPopularity.entrySet().stream()
				.sorted((a, b) -> b.getValue() - a.getValue()) // Sort by popularity
				.limit(5) // Top 5 products
				.map(Map.Entry::getKey)
				.toList();
	}

}
