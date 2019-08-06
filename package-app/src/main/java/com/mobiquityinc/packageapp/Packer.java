package com.mobiquityinc.packageapp;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.mobiquityinc.packageapp.comparator.ItemComparatorPrice;
import com.mobiquityinc.packageapp.entity.Candidate;
import com.mobiquityinc.packageapp.entity.Item;
import com.mobiquityinc.packageapp.entity.Pack;
import com.mobiquityinc.packageapp.exception.APIException;
import com.mobiquityinc.packageapp.util.PackerConstants;

@Component
public class Packer {

	private static ItemComparatorPrice itemCompPrice = new ItemComparatorPrice();

	public static void main(String args[]) throws APIException {
		System.out.println(pack("c://DEV//file.txt"));
	}

	public static String pack(String filePath) throws APIException {
		StringBuilder sbResult = new StringBuilder();

		//Read the file and transform the date in Java Objects...
		List<Pack> packages = transformFileToPackage(filePath);

		for (Pack pack : packages) {
			Collections.sort(pack.getItems(), itemCompPrice); //sort items by the price/less weight

			//Filter only items that are less than the max package`s weight capacity
			List<Item> itemsLessThanMaxWeight = pack.getItems()
					.stream()
					.filter(item -> item.getWeight() <= pack.getMaxWeight())
					.collect(Collectors.toList());

			if (itemsLessThanMaxWeight.isEmpty()) {
				sbResult.append(PackerConstants.NONE + "\n");
				continue;
			}
			// create all possibilities, called as candidates, that can fit in the package
			List<Candidate> candidates = createCandidates(pack.getMaxWeight(), itemsLessThanMaxWeight);
			Collections.sort(candidates); //sort them by totalPrice (sum of all items inside)
			List<Item> resultCandidateItem = candidates.get(0).getCandidateItems();// get the best one
			Collections.sort(resultCandidateItem); //organizing the best candidate by index
			sbResult.append(resultCandidateItem.toString() + "\n");
		}
		return sbResult.toString();
	}

	private static List<Candidate> createCandidates(Double packMaxWeight, List<Item> items) {
		List<Candidate> candidates = new ArrayList<>();

		for(int i = 0; i < items.size(); i++) {
			Candidate candidate = new Candidate();
			updateCandidate(candidate, items.get(i));

			for(int j = 0; j < items.size(); j++) {
				if (j <= i) {
					continue;
				}
				//Calculate if can add one more item in the package
				if (candidate.getTotalWeight() + items.get(j).getWeight() > packMaxWeight) {
					continue;
				}
				updateCandidate(candidate, items.get(j));
			}   
			candidates.add(candidate);
		}

		return candidates;
	}

	private static void updateCandidate(Candidate candidate, Item item) {
		candidate.getCandidateItems().add(item);
		candidate.setTotalWeight(candidate.getTotalWeight() + item.getWeight());
		candidate.setTotalPrice(candidate.getTotalPrice() + item.getPrice());
	}

	private static List<Pack> transformFileToPackage(String filePath) throws APIException {
		if (StringUtils.isEmpty(filePath)) {
			throw new APIException("No filePath has been provided");
		}

		List<Pack> packages = new ArrayList<>();
		int packageId = 1;

		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = br.readLine()) != null) {

				if (!StringUtils.isEmpty(line)) {
					String[] split = line.split(":");
					Double maxWeight = Double.parseDouble(split[0].trim());

					if (maxWeight > PackerConstants.APP_MAX_PACKAGE_WEIGHT) {
						throw new APIException("Max weight that a package can take is " + PackerConstants.APP_MAX_PACKAGE_WEIGHT);
					}

					String[] itemsSplit = split[1].split("\\)");
					List<Item> itemList = extractItems(itemsSplit);
					packages.add(new Pack(packageId, maxWeight, itemList));
				}
			}
		} catch (IOException e) {
			throw new APIException("The system cannot find the file specified");
		}
		return packages;
	}

	private static List<Item> extractItems(String[] itemsSplit) throws APIException {
		List<Item> items = new ArrayList<>();

		for(int i = 0; i < itemsSplit.length; i++) {
			int commaIndex = itemsSplit[i].indexOf(",");
			int index = Integer.parseInt(itemsSplit[i].substring(2, commaIndex));
			commaIndex++;
			double weight = Double.parseDouble(itemsSplit[i].substring(commaIndex,  itemsSplit[i].indexOf(",",  commaIndex)));
			double price = Double.parseDouble(itemsSplit[i].substring(itemsSplit[i].indexOf(PackerConstants.CURRENCY_SIMBOL) + 1 , itemsSplit[i].length()));

			if (price > PackerConstants.APP_MAX_ITEM_COST) {
				throw new APIException("Max cost of an item is " + PackerConstants.APP_MAX_ITEM_COST);
			}

			if (weight > PackerConstants.APP_MAX_ITEM_WEIGHT) {
				throw new APIException("Max weight of an item is " + PackerConstants.APP_MAX_ITEM_WEIGHT);
			}

			items.add(new Item(index, weight, price));
		}
		return items;
	}
}
