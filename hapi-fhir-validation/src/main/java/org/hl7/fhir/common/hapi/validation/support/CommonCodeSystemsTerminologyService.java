package org.hl7.fhir.common.hapi.validation.support;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.util.ClasspathUtil;
import org.apache.commons.lang3.Validate;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;
import org.hl7.fhir.dstu2.model.ValueSet;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * This {@link IValidationSupport validation support module} can be used to validate codes against common
 * CodeSystems that are commonly used, but are not distriuted with the FHIR specification for various reasons
 * (size, complexity, etc.).
 * <p>
 * See <a href="https://hapifhir.io/hapi-fhir/docs/validation/validation_support_modules.html#CommonCodeSystemsTerminologyService">CommonCodeSystemsTerminologyService</a> in the HAPI FHIR documentation
 * for details about what is and isn't covered by this class.
 * </p>
 */
public class CommonCodeSystemsTerminologyService implements IValidationSupport {
	public static final String LANGUAGES_VALUESET_URL = "http://hl7.org/fhir/ValueSet/languages";
	public static final String MIMETYPES_VALUESET_URL = "http://hl7.org/fhir/ValueSet/mimetypes";
	public static final String MIMETYPES_CODESYSTEM_URL = "urn:ietf:bcp:13";
	public static final String CURRENCIES_CODESYSTEM_URL = "urn:iso:std:iso:4217";
	public static final String CURRENCIES_VALUESET_URL = "http://hl7.org/fhir/ValueSet/currencies";
	public static final String COUNTRIES_CODESYSTEM_URL = "urn:iso:std:iso:3166";
	public static final String UCUM_CODESYSTEM_URL = "http://unitsofmeasure.org";
	public static final String UCUM_VALUESET_URL = "http://hl7.org/fhir/ValueSet/ucum-units";
	private static final String USPS_CODESYSTEM_URL = "https://www.usps.com/";
	private static final String USPS_VALUESET_URL = "http://hl7.org/fhir/us/core/ValueSet/us-core-usps-state";
	private static final Logger ourLog = LoggerFactory.getLogger(CommonCodeSystemsTerminologyService.class);
	private static Map<String, String> USPS_CODES = Collections.unmodifiableMap(buildUspsCodes());
	private static Map<String, String> ISO_4217_CODES = Collections.unmodifiableMap(buildIso4217Codes());
	private static Map<String, String> ISO_3166_CODES = Collections.unmodifiableMap(buildIso3166Codes());
	private final FhirContext myFhirContext;

	/**
	 * Constructor
	 */
	public CommonCodeSystemsTerminologyService(FhirContext theFhirContext) {
		Validate.notNull(theFhirContext);

		myFhirContext = theFhirContext;
	}

	@Override
	public CodeValidationResult validateCodeInValueSet(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theOptions, String theCodeSystem, String theCode, String theDisplay, @Nonnull IBaseResource theValueSet) {
		String url = getValueSetUrl(theValueSet);
		return validateCode(theValidationSupportContext, theOptions, theCodeSystem, theCode, theDisplay, url);
	}

	@Override
	public CodeValidationResult validateCode(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theOptions, String theCodeSystem, String theCode, String theDisplay, String theValueSetUrl) {
		/* **************************************************************************************
		 * NOTE: Update validation_support_modules.html if any of the support in this module
		 * changes in any way!
		 * **************************************************************************************/

		Map<String, String> handlerMap = null;
		String expectSystem = null;
		switch (defaultString(theValueSetUrl)) {
			case USPS_VALUESET_URL:
				handlerMap = USPS_CODES;
				expectSystem = USPS_CODESYSTEM_URL;
				break;

			case CURRENCIES_VALUESET_URL:
				handlerMap = ISO_4217_CODES;
				expectSystem = CURRENCIES_CODESYSTEM_URL;
				break;

			case LANGUAGES_VALUESET_URL:
			case MIMETYPES_VALUESET_URL:
				// This is a pretty naive implementation - Should be enhanced in future
				return new CodeValidationResult()
					.setCode(theCode)
					.setDisplay(theDisplay);

			case UCUM_VALUESET_URL: {
				String system = theCodeSystem;
				if (system == null && theOptions.isInferSystem()) {
					system = UCUM_CODESYSTEM_URL;
				}
				CodeValidationResult validationResult = validateLookupCode(theValidationSupportContext, theCode, system);
				if (validationResult != null) {
					return validationResult;
				}
			}
		}

		if (handlerMap != null) {
			String display = handlerMap.get(theCode);
			if (display != null) {
				if (expectSystem.equals(theCodeSystem) || theOptions.isInferSystem()) {
					return new CodeValidationResult()
						.setCode(theCode)
						.setDisplay(display);
				}
			}

			return new CodeValidationResult()
				.setSeverity(IssueSeverity.ERROR)
				.setMessage("Code \"" + theCode + "\" is not in system: " + USPS_CODESYSTEM_URL);
		}

		if (isBlank(theValueSetUrl)) {
			CodeValidationResult validationResult = validateLookupCode(theValidationSupportContext, theCode, theCodeSystem);
			if (validationResult != null) {
				return validationResult;
			}
		}

		return null;
	}

	@Nullable
	public CodeValidationResult validateLookupCode(ValidationSupportContext theValidationSupportContext, String theCode, String theSystem) {
		LookupCodeResult lookupResult = lookupCode(theValidationSupportContext, theSystem, theCode);
		CodeValidationResult validationResult = null;
		if (lookupResult != null) {
			if (lookupResult.isFound()) {
				validationResult = new CodeValidationResult()
					.setCode(lookupResult.getSearchedForCode())
					.setDisplay(lookupResult.getCodeDisplay());
			}
		}
		return validationResult;
	}


	@Override
	public LookupCodeResult lookupCode(ValidationSupportContext theValidationSupportContext, String theSystem, String theCode) {

		switch (theSystem) {
			case UCUM_CODESYSTEM_URL:

				InputStream input = ClasspathUtil.loadResourceAsStream("/ucum-essence.xml");
				try {
					UcumEssenceService svc = new UcumEssenceService(input);
					String outcome = svc.analyse(theCode);
					if (outcome != null) {

						LookupCodeResult retVal = new LookupCodeResult();
						retVal.setSearchedForCode(theCode);
						retVal.setSearchedForSystem(theSystem);
						retVal.setFound(true);
						retVal.setCodeDisplay(outcome);
						return retVal;

					}
				} catch (UcumException e) {
					ourLog.debug("Failed parse UCUM code: {}", theCode, e);
					return null;
				} finally {
					ClasspathUtil.close(input);
				}
				break;

			case COUNTRIES_CODESYSTEM_URL:

				String display = ISO_3166_CODES.get(theCode);
				if (isNotBlank(display)) {
					LookupCodeResult retVal = new LookupCodeResult();
					retVal.setSearchedForCode(theCode);
					retVal.setSearchedForSystem(theSystem);
					retVal.setFound(true);
					retVal.setCodeDisplay(display);
					return retVal;
				}
				break;

			case MIMETYPES_CODESYSTEM_URL:

				// This is a pretty naive implementation - Should be enhanced in future
				LookupCodeResult retVal = new LookupCodeResult();
				retVal.setSearchedForCode(theCode);
				retVal.setSearchedForSystem(theSystem);
				retVal.setFound(true);
				return retVal;

			default:

				return null;

		}

		// If we get here it means we know the codesystem but the code was bad
		LookupCodeResult retVal = new LookupCodeResult();
		retVal.setSearchedForCode(theCode);
		retVal.setSearchedForSystem(theSystem);
		retVal.setFound(false);
		return retVal;

	}

	@Override
	public boolean isCodeSystemSupported(ValidationSupportContext theValidationSupportContext, String theSystem) {

		switch (theSystem) {
			case COUNTRIES_CODESYSTEM_URL:
			case UCUM_CODESYSTEM_URL:
			case MIMETYPES_CODESYSTEM_URL:
				return true;
		}

		return false;
	}

	@Override
	public boolean isValueSetSupported(ValidationSupportContext theValidationSupportContext, String theValueSetUrl) {

		switch (theValueSetUrl) {
			case CURRENCIES_VALUESET_URL:
			case LANGUAGES_VALUESET_URL:
			case MIMETYPES_VALUESET_URL:
			case UCUM_VALUESET_URL:
			case USPS_VALUESET_URL:
				return true;
		}

		return false;
	}

	@Override
	public FhirContext getFhirContext() {
		return myFhirContext;
	}

	public static String getValueSetUrl(@Nonnull IBaseResource theValueSet) {
		String url;
		switch (theValueSet.getStructureFhirVersionEnum()) {
			case DSTU2: {
				url = ((ca.uhn.fhir.model.dstu2.resource.ValueSet) theValueSet).getUrl();
				break;
			}
			case DSTU2_HL7ORG: {
				url = ((ValueSet) theValueSet).getUrl();
				break;
			}
			case DSTU3: {
				url = ((org.hl7.fhir.dstu3.model.ValueSet) theValueSet).getUrl();
				break;
			}
			case R4: {
				url = ((org.hl7.fhir.r4.model.ValueSet) theValueSet).getUrl();
				break;
			}
			case R5: {
				url = ((org.hl7.fhir.r5.model.ValueSet) theValueSet).getUrl();
				break;
			}
			case DSTU2_1:
			default:
				throw new IllegalArgumentException("Can not handle version: " + theValueSet.getStructureFhirVersionEnum());
		}
		return url;
	}

	private static HashMap<String, String> buildUspsCodes() {
		HashMap<String, String> uspsCodes = new HashMap<>();
		uspsCodes.put("AK", "Alaska");
		uspsCodes.put("AL", "Alabama");
		uspsCodes.put("AR", "Arkansas");
		uspsCodes.put("AS", "American Samoa");
		uspsCodes.put("AZ", "Arizona");
		uspsCodes.put("CA", "California");
		uspsCodes.put("CO", "Colorado");
		uspsCodes.put("CT", "Connecticut");
		uspsCodes.put("DC", "District of Columbia");
		uspsCodes.put("DE", "Delaware");
		uspsCodes.put("FL", "Florida");
		uspsCodes.put("FM", "Federated States of Micronesia");
		uspsCodes.put("GA", "Georgia");
		uspsCodes.put("GU", "Guam");
		uspsCodes.put("HI", "Hawaii");
		uspsCodes.put("IA", "Iowa");
		uspsCodes.put("ID", "Idaho");
		uspsCodes.put("IL", "Illinois");
		uspsCodes.put("IN", "Indiana");
		uspsCodes.put("KS", "Kansas");
		uspsCodes.put("KY", "Kentucky");
		uspsCodes.put("LA", "Louisiana");
		uspsCodes.put("MA", "Massachusetts");
		uspsCodes.put("MD", "Maryland");
		uspsCodes.put("ME", "Maine");
		uspsCodes.put("MH", "Marshall Islands");
		uspsCodes.put("MI", "Michigan");
		uspsCodes.put("MN", "Minnesota");
		uspsCodes.put("MO", "Missouri");
		uspsCodes.put("MP", "Northern Mariana Islands");
		uspsCodes.put("MS", "Mississippi");
		uspsCodes.put("MT", "Montana");
		uspsCodes.put("NC", "North Carolina");
		uspsCodes.put("ND", "North Dakota");
		uspsCodes.put("NE", "Nebraska");
		uspsCodes.put("NH", "New Hampshire");
		uspsCodes.put("NJ", "New Jersey");
		uspsCodes.put("NM", "New Mexico");
		uspsCodes.put("NV", "Nevada");
		uspsCodes.put("NY", "New York");
		uspsCodes.put("OH", "Ohio");
		uspsCodes.put("OK", "Oklahoma");
		uspsCodes.put("OR", "Oregon");
		uspsCodes.put("PA", "Pennsylvania");
		uspsCodes.put("PR", "Puerto Rico");
		uspsCodes.put("PW", "Palau");
		uspsCodes.put("RI", "Rhode Island");
		uspsCodes.put("SC", "South Carolina");
		uspsCodes.put("SD", "South Dakota");
		uspsCodes.put("TN", "Tennessee");
		uspsCodes.put("TX", "Texas");
		uspsCodes.put("UM", "U.S. Minor Outlying Islands");
		uspsCodes.put("UT", "Utah");
		uspsCodes.put("VA", "Virginia");
		uspsCodes.put("VI", "Virgin Islands of the U.S.");
		uspsCodes.put("VT", "Vermont");
		uspsCodes.put("WA", "Washington");
		uspsCodes.put("WI", "Wisconsin");
		uspsCodes.put("WV", "West Virginia");
		uspsCodes.put("WY", "Wyoming");
		return uspsCodes;
	}

	private static HashMap<String, String> buildIso4217Codes() {
		HashMap<String, String> iso4217Codes = new HashMap<>();
		iso4217Codes.put("AED", "United Arab Emirates dirham");
		iso4217Codes.put("AFN", "Afghan afghani");
		iso4217Codes.put("ALL", "Albanian lek");
		iso4217Codes.put("AMD", "Armenian dram");
		iso4217Codes.put("ANG", "Netherlands Antillean guilder");
		iso4217Codes.put("AOA", "Angolan kwanza");
		iso4217Codes.put("ARS", "Argentine peso");
		iso4217Codes.put("AUD", "Australian dollar");
		iso4217Codes.put("AWG", "Aruban florin");
		iso4217Codes.put("AZN", "Azerbaijani manat");
		iso4217Codes.put("BAM", "Bosnia and Herzegovina convertible mark");
		iso4217Codes.put("BBD", "Barbados dollar");
		iso4217Codes.put("BDT", "Bangladeshi taka");
		iso4217Codes.put("BGN", "Bulgarian lev");
		iso4217Codes.put("BHD", "Bahraini dinar");
		iso4217Codes.put("BIF", "Burundian franc");
		iso4217Codes.put("BMD", "Bermudian dollar");
		iso4217Codes.put("BND", "Brunei dollar");
		iso4217Codes.put("BOB", "Boliviano");
		iso4217Codes.put("BOV", "Bolivian Mvdol (funds code)");
		iso4217Codes.put("BRL", "Brazilian real");
		iso4217Codes.put("BSD", "Bahamian dollar");
		iso4217Codes.put("BTN", "Bhutanese ngultrum");
		iso4217Codes.put("BWP", "Botswana pula");
		iso4217Codes.put("BYN", "Belarusian ruble");
		iso4217Codes.put("BZD", "Belize dollar");
		iso4217Codes.put("CAD", "Canadian dollar");
		iso4217Codes.put("CDF", "Congolese franc");
		iso4217Codes.put("CHE", "WIR Euro (complementary currency)");
		iso4217Codes.put("CHF", "Swiss franc");
		iso4217Codes.put("CHW", "WIR Franc (complementary currency)");
		iso4217Codes.put("CLF", "Unidad de Fomento (funds code)");
		iso4217Codes.put("CLP", "Chilean peso");
		iso4217Codes.put("CNY", "Renminbi (Chinese) yuan[8]");
		iso4217Codes.put("COP", "Colombian peso");
		iso4217Codes.put("COU", "Unidad de Valor Real (UVR) (funds code)[9]");
		iso4217Codes.put("CRC", "Costa Rican colon");
		iso4217Codes.put("CUC", "Cuban convertible peso");
		iso4217Codes.put("CUP", "Cuban peso");
		iso4217Codes.put("CVE", "Cape Verde escudo");
		iso4217Codes.put("CZK", "Czech koruna");
		iso4217Codes.put("DJF", "Djiboutian franc");
		iso4217Codes.put("DKK", "Danish krone");
		iso4217Codes.put("DOP", "Dominican peso");
		iso4217Codes.put("DZD", "Algerian dinar");
		iso4217Codes.put("EGP", "Egyptian pound");
		iso4217Codes.put("ERN", "Eritrean nakfa");
		iso4217Codes.put("ETB", "Ethiopian birr");
		iso4217Codes.put("EUR", "Euro");
		iso4217Codes.put("FJD", "Fiji dollar");
		iso4217Codes.put("FKP", "Falkland Islands pound");
		iso4217Codes.put("GBP", "Pound sterling");
		iso4217Codes.put("GEL", "Georgian lari");
		iso4217Codes.put("GGP", "Guernsey Pound");
		iso4217Codes.put("GHS", "Ghanaian cedi");
		iso4217Codes.put("GIP", "Gibraltar pound");
		iso4217Codes.put("GMD", "Gambian dalasi");
		iso4217Codes.put("GNF", "Guinean franc");
		iso4217Codes.put("GTQ", "Guatemalan quetzal");
		iso4217Codes.put("GYD", "Guyanese dollar");
		iso4217Codes.put("HKD", "Hong Kong dollar");
		iso4217Codes.put("HNL", "Honduran lempira");
		iso4217Codes.put("HRK", "Croatian kuna");
		iso4217Codes.put("HTG", "Haitian gourde");
		iso4217Codes.put("HUF", "Hungarian forint");
		iso4217Codes.put("IDR", "Indonesian rupiah");
		iso4217Codes.put("ILS", "Israeli new shekel");
		iso4217Codes.put("IMP", "Isle of Man Pound");
		iso4217Codes.put("INR", "Indian rupee");
		iso4217Codes.put("IQD", "Iraqi dinar");
		iso4217Codes.put("IRR", "Iranian rial");
		iso4217Codes.put("ISK", "Icelandic króna");
		iso4217Codes.put("JEP", "Jersey Pound");
		iso4217Codes.put("JMD", "Jamaican dollar");
		iso4217Codes.put("JOD", "Jordanian dinar");
		iso4217Codes.put("JPY", "Japanese yen");
		iso4217Codes.put("KES", "Kenyan shilling");
		iso4217Codes.put("KGS", "Kyrgyzstani som");
		iso4217Codes.put("KHR", "Cambodian riel");
		iso4217Codes.put("KMF", "Comoro franc");
		iso4217Codes.put("KPW", "North Korean won");
		iso4217Codes.put("KRW", "South Korean won");
		iso4217Codes.put("KWD", "Kuwaiti dinar");
		iso4217Codes.put("KYD", "Cayman Islands dollar");
		iso4217Codes.put("KZT", "Kazakhstani tenge");
		iso4217Codes.put("LAK", "Lao kip");
		iso4217Codes.put("LBP", "Lebanese pound");
		iso4217Codes.put("LKR", "Sri Lankan rupee");
		iso4217Codes.put("LRD", "Liberian dollar");
		iso4217Codes.put("LSL", "Lesotho loti");
		iso4217Codes.put("LYD", "Libyan dinar");
		iso4217Codes.put("MAD", "Moroccan dirham");
		iso4217Codes.put("MDL", "Moldovan leu");
		iso4217Codes.put("MGA", "Malagasy ariary");
		iso4217Codes.put("MKD", "Macedonian denar");
		iso4217Codes.put("MMK", "Myanmar kyat");
		iso4217Codes.put("MNT", "Mongolian tögrög");
		iso4217Codes.put("MOP", "Macanese pataca");
		iso4217Codes.put("MRU", "Mauritanian ouguiya");
		iso4217Codes.put("MUR", "Mauritian rupee");
		iso4217Codes.put("MVR", "Maldivian rufiyaa");
		iso4217Codes.put("MWK", "Malawian kwacha");
		iso4217Codes.put("MXN", "Mexican peso");
		iso4217Codes.put("MXV", "Mexican Unidad de Inversion (UDI) (funds code)");
		iso4217Codes.put("MYR", "Malaysian ringgit");
		iso4217Codes.put("MZN", "Mozambican metical");
		iso4217Codes.put("NAD", "Namibian dollar");
		iso4217Codes.put("NGN", "Nigerian naira");
		iso4217Codes.put("NIO", "Nicaraguan córdoba");
		iso4217Codes.put("NOK", "Norwegian krone");
		iso4217Codes.put("NPR", "Nepalese rupee");
		iso4217Codes.put("NZD", "New Zealand dollar");
		iso4217Codes.put("OMR", "Omani rial");
		iso4217Codes.put("PAB", "Panamanian balboa");
		iso4217Codes.put("PEN", "Peruvian Sol");
		iso4217Codes.put("PGK", "Papua New Guinean kina");
		iso4217Codes.put("PHP", "Philippine piso[13]");
		iso4217Codes.put("PKR", "Pakistani rupee");
		iso4217Codes.put("PLN", "Polish złoty");
		iso4217Codes.put("PYG", "Paraguayan guaraní");
		iso4217Codes.put("QAR", "Qatari riyal");
		iso4217Codes.put("RON", "Romanian leu");
		iso4217Codes.put("RSD", "Serbian dinar");
		iso4217Codes.put("RUB", "Russian ruble");
		iso4217Codes.put("RWF", "Rwandan franc");
		iso4217Codes.put("SAR", "Saudi riyal");
		iso4217Codes.put("SBD", "Solomon Islands dollar");
		iso4217Codes.put("SCR", "Seychelles rupee");
		iso4217Codes.put("SDG", "Sudanese pound");
		iso4217Codes.put("SEK", "Swedish krona/kronor");
		iso4217Codes.put("SGD", "Singapore dollar");
		iso4217Codes.put("SHP", "Saint Helena pound");
		iso4217Codes.put("SLL", "Sierra Leonean leone");
		iso4217Codes.put("SOS", "Somali shilling");
		iso4217Codes.put("SRD", "Surinamese dollar");
		iso4217Codes.put("SSP", "South Sudanese pound");
		iso4217Codes.put("STN", "São Tomé and Príncipe dobra");
		iso4217Codes.put("SVC", "Salvadoran colón");
		iso4217Codes.put("SYP", "Syrian pound");
		iso4217Codes.put("SZL", "Swazi lilangeni");
		iso4217Codes.put("THB", "Thai baht");
		iso4217Codes.put("TJS", "Tajikistani somoni");
		iso4217Codes.put("TMT", "Turkmenistan manat");
		iso4217Codes.put("TND", "Tunisian dinar");
		iso4217Codes.put("TOP", "Tongan paʻanga");
		iso4217Codes.put("TRY", "Turkish lira");
		iso4217Codes.put("TTD", "Trinidad and Tobago dollar");
		iso4217Codes.put("TVD", "Tuvalu Dollar");
		iso4217Codes.put("TWD", "New Taiwan dollar");
		iso4217Codes.put("TZS", "Tanzanian shilling");
		iso4217Codes.put("UAH", "Ukrainian hryvnia");
		iso4217Codes.put("UGX", "Ugandan shilling");
		iso4217Codes.put("USD", "United States dollar");
		iso4217Codes.put("USN", "United States dollar (next day) (funds code)");
		iso4217Codes.put("UYI", "Uruguay Peso en Unidades Indexadas (URUIURUI) (funds code)");
		iso4217Codes.put("UYU", "Uruguayan peso");
		iso4217Codes.put("UZS", "Uzbekistan som");
		iso4217Codes.put("VEF", "Venezuelan bolívar");
		iso4217Codes.put("VND", "Vietnamese đồng");
		iso4217Codes.put("VUV", "Vanuatu vatu");
		iso4217Codes.put("WST", "Samoan tala");
		iso4217Codes.put("XAF", "CFA franc BEAC");
		iso4217Codes.put("XAG", "Silver (one troy ounce)");
		iso4217Codes.put("XAU", "Gold (one troy ounce)");
		iso4217Codes.put("XBA", "European Composite Unit (EURCO) (bond market unit)");
		iso4217Codes.put("XBB", "European Monetary Unit (E.M.U.-6) (bond market unit)");
		iso4217Codes.put("XBC", "European Unit of Account 9 (E.U.A.-9) (bond market unit)");
		iso4217Codes.put("XBD", "European Unit of Account 17 (E.U.A.-17) (bond market unit)");
		iso4217Codes.put("XCD", "East Caribbean dollar");
		iso4217Codes.put("XDR", "Special drawing rights");
		iso4217Codes.put("XOF", "CFA franc BCEAO");
		iso4217Codes.put("XPD", "Palladium (one troy ounce)");
		iso4217Codes.put("XPF", "CFP franc (franc Pacifique)");
		iso4217Codes.put("XPT", "Platinum (one troy ounce)");
		iso4217Codes.put("XSU", "SUCRE");
		iso4217Codes.put("XTS", "Code reserved for testing purposes");
		iso4217Codes.put("XUA", "ADB Unit of Account");
		iso4217Codes.put("XXX", "No currency");
		iso4217Codes.put("YER", "Yemeni rial");
		iso4217Codes.put("ZAR", "South African rand");
		iso4217Codes.put("ZMW", "Zambian kwacha");
		iso4217Codes.put("ZWL", "Zimbabwean dollar A/10");
		return iso4217Codes;
	}


	private static HashMap<String, String> buildIso3166Codes() {
		HashMap<String, String> codes = new HashMap<>();
		codes.put("AF", "Afghanistan");
		codes.put("AX", "Åland Islands");
		codes.put("AL", "Albania");
		codes.put("DZ", "Algeria");
		codes.put("AS", "American Samoa");
		codes.put("AD", "Andorra");
		codes.put("AO", "Angola");
		codes.put("AI", "Anguilla");
		codes.put("AQ", "Antarctica");
		codes.put("AG", "Antigua & Barbuda");
		codes.put("AR", "Argentina");
		codes.put("AM", "Armenia");
		codes.put("AW", "Aruba");
		codes.put("AU", "Australia");
		codes.put("AT", "Austria");
		codes.put("AZ", "Azerbaijan");
		codes.put("BS", "Bahamas");
		codes.put("BH", "Bahrain");
		codes.put("BD", "Bangladesh");
		codes.put("BB", "Barbados");
		codes.put("BY", "Belarus");
		codes.put("BE", "Belgium");
		codes.put("BZ", "Belize");
		codes.put("BJ", "Benin");
		codes.put("BM", "Bermuda");
		codes.put("BT", "Bhutan");
		codes.put("BO", "Bolivia");
		codes.put("BA", "Bosnia & Herzegovina");
		codes.put("BW", "Botswana");
		codes.put("BV", "Bouvet Island");
		codes.put("BR", "Brazil");
		codes.put("IO", "British Indian Ocean Territory");
		codes.put("VG", "British Virgin Islands");
		codes.put("BN", "Brunei");
		codes.put("BG", "Bulgaria");
		codes.put("BF", "Burkina Faso");
		codes.put("BI", "Burundi");
		codes.put("KH", "Cambodia");
		codes.put("CM", "Cameroon");
		codes.put("CA", "Canada");
		codes.put("CV", "Cape Verde");
		codes.put("BQ", "Caribbean Netherlands");
		codes.put("KY", "Cayman Islands");
		codes.put("CF", "Central African Republic");
		codes.put("TD", "Chad");
		codes.put("CL", "Chile");
		codes.put("CN", "China");
		codes.put("CX", "Christmas Island");
		codes.put("CC", "Cocos (Keeling) Islands");
		codes.put("CO", "Colombia");
		codes.put("KM", "Comoros");
		codes.put("CG", "Congo - Brazzaville");
		codes.put("CD", "Congo - Kinshasa");
		codes.put("CK", "Cook Islands");
		codes.put("CR", "Costa Rica");
		codes.put("CI", "Côte d’Ivoire");
		codes.put("HR", "Croatia");
		codes.put("CU", "Cuba");
		codes.put("CW", "Curaçao");
		codes.put("CY", "Cyprus");
		codes.put("CZ", "Czechia");
		codes.put("DK", "Denmark");
		codes.put("DJ", "Djibouti");
		codes.put("DM", "Dominica");
		codes.put("DO", "Dominican Republic");
		codes.put("EC", "Ecuador");
		codes.put("EG", "Egypt");
		codes.put("SV", "El Salvador");
		codes.put("GQ", "Equatorial Guinea");
		codes.put("ER", "Eritrea");
		codes.put("EE", "Estonia");
		codes.put("SZ", "Eswatini");
		codes.put("ET", "Ethiopia");
		codes.put("FK", "Falkland Islands");
		codes.put("FO", "Faroe Islands");
		codes.put("FJ", "Fiji");
		codes.put("FI", "Finland");
		codes.put("FR", "France");
		codes.put("GF", "French Guiana");
		codes.put("PF", "French Polynesia");
		codes.put("TF", "French Southern Territories");
		codes.put("GA", "Gabon");
		codes.put("GM", "Gambia");
		codes.put("GE", "Georgia");
		codes.put("DE", "Germany");
		codes.put("GH", "Ghana");
		codes.put("GI", "Gibraltar");
		codes.put("GR", "Greece");
		codes.put("GL", "Greenland");
		codes.put("GD", "Grenada");
		codes.put("GP", "Guadeloupe");
		codes.put("GU", "Guam");
		codes.put("GT", "Guatemala");
		codes.put("GG", "Guernsey");
		codes.put("GN", "Guinea");
		codes.put("GW", "Guinea-Bissau");
		codes.put("GY", "Guyana");
		codes.put("HT", "Haiti");
		codes.put("HM", "Heard & McDonald Islands");
		codes.put("HN", "Honduras");
		codes.put("HK", "Hong Kong SAR China");
		codes.put("HU", "Hungary");
		codes.put("IS", "Iceland");
		codes.put("IN", "India");
		codes.put("ID", "Indonesia");
		codes.put("IR", "Iran");
		codes.put("IQ", "Iraq");
		codes.put("IE", "Ireland");
		codes.put("IM", "Isle of Man");
		codes.put("IL", "Israel");
		codes.put("IT", "Italy");
		codes.put("JM", "Jamaica");
		codes.put("JP", "Japan");
		codes.put("JE", "Jersey");
		codes.put("JO", "Jordan");
		codes.put("KZ", "Kazakhstan");
		codes.put("KE", "Kenya");
		codes.put("KI", "Kiribati");
		codes.put("KW", "Kuwait");
		codes.put("KG", "Kyrgyzstan");
		codes.put("LA", "Laos");
		codes.put("LV", "Latvia");
		codes.put("LB", "Lebanon");
		codes.put("LS", "Lesotho");
		codes.put("LR", "Liberia");
		codes.put("LY", "Libya");
		codes.put("LI", "Liechtenstein");
		codes.put("LT", "Lithuania");
		codes.put("LU", "Luxembourg");
		codes.put("MO", "Macao SAR China");
		codes.put("MG", "Madagascar");
		codes.put("MW", "Malawi");
		codes.put("MY", "Malaysia");
		codes.put("MV", "Maldives");
		codes.put("ML", "Mali");
		codes.put("MT", "Malta");
		codes.put("MH", "Marshall Islands");
		codes.put("MQ", "Martinique");
		codes.put("MR", "Mauritania");
		codes.put("MU", "Mauritius");
		codes.put("YT", "Mayotte");
		codes.put("MX", "Mexico");
		codes.put("FM", "Micronesia");
		codes.put("MD", "Moldova");
		codes.put("MC", "Monaco");
		codes.put("MN", "Mongolia");
		codes.put("ME", "Montenegro");
		codes.put("MS", "Montserrat");
		codes.put("MA", "Morocco");
		codes.put("MZ", "Mozambique");
		codes.put("MM", "Myanmar (Burma)");
		codes.put("NA", "Namibia");
		codes.put("NR", "Nauru");
		codes.put("NP", "Nepal");
		codes.put("NL", "Netherlands");
		codes.put("NC", "New Caledonia");
		codes.put("NZ", "New Zealand");
		codes.put("NI", "Nicaragua");
		codes.put("NE", "Niger");
		codes.put("NG", "Nigeria");
		codes.put("NU", "Niue");
		codes.put("NF", "Norfolk Island");
		codes.put("KP", "North Korea");
		codes.put("MK", "North Macedonia");
		codes.put("MP", "Northern Mariana Islands");
		codes.put("NO", "Norway");
		codes.put("OM", "Oman");
		codes.put("PK", "Pakistan");
		codes.put("PW", "Palau");
		codes.put("PS", "Palestinian Territories");
		codes.put("PA", "Panama");
		codes.put("PG", "Papua New Guinea");
		codes.put("PY", "Paraguay");
		codes.put("PE", "Peru");
		codes.put("PH", "Philippines");
		codes.put("PN", "Pitcairn Islands");
		codes.put("PL", "Poland");
		codes.put("PT", "Portugal");
		codes.put("PR", "Puerto Rico");
		codes.put("QA", "Qatar");
		codes.put("RE", "Réunion");
		codes.put("RO", "Romania");
		codes.put("RU", "Russia");
		codes.put("RW", "Rwanda");
		codes.put("WS", "Samoa");
		codes.put("SM", "San Marino");
		codes.put("ST", "São Tomé & Príncipe");
		codes.put("SA", "Saudi Arabia");
		codes.put("SN", "Senegal");
		codes.put("RS", "Serbia");
		codes.put("SC", "Seychelles");
		codes.put("SL", "Sierra Leone");
		codes.put("SG", "Singapore");
		codes.put("SX", "Sint Maarten");
		codes.put("SK", "Slovakia");
		codes.put("SI", "Slovenia");
		codes.put("SB", "Solomon Islands");
		codes.put("SO", "Somalia");
		codes.put("ZA", "South Africa");
		codes.put("GS", "South Georgia & South Sandwich Islands");
		codes.put("KR", "South Korea");
		codes.put("SS", "South Sudan");
		codes.put("ES", "Spain");
		codes.put("LK", "Sri Lanka");
		codes.put("BL", "St. Barthélemy");
		codes.put("SH", "St. Helena");
		codes.put("KN", "St. Kitts & Nevis");
		codes.put("LC", "St. Lucia");
		codes.put("MF", "St. Martin");
		codes.put("PM", "St. Pierre & Miquelon");
		codes.put("VC", "St. Vincent & Grenadines");
		codes.put("SD", "Sudan");
		codes.put("SR", "Suriname");
		codes.put("SJ", "Svalbard & Jan Mayen");
		codes.put("SE", "Sweden");
		codes.put("CH", "Switzerland");
		codes.put("SY", "Syria");
		codes.put("TW", "Taiwan");
		codes.put("TJ", "Tajikistan");
		codes.put("TZ", "Tanzania");
		codes.put("TH", "Thailand");
		codes.put("TL", "Timor-Leste");
		codes.put("TG", "Togo");
		codes.put("TK", "Tokelau");
		codes.put("TO", "Tonga");
		codes.put("TT", "Trinidad & Tobago");
		codes.put("TN", "Tunisia");
		codes.put("TR", "Turkey");
		codes.put("TM", "Turkmenistan");
		codes.put("TC", "Turks & Caicos Islands");
		codes.put("TV", "Tuvalu");
		codes.put("UM", "U.S. Outlying Islands");
		codes.put("VI", "U.S. Virgin Islands");
		codes.put("UG", "Uganda");
		codes.put("UA", "Ukraine");
		codes.put("AE", "United Arab Emirates");
		codes.put("GB", "United Kingdom");
		codes.put("US", "United States");
		codes.put("UY", "Uruguay");
		codes.put("UZ", "Uzbekistan");
		codes.put("VU", "Vanuatu");
		codes.put("VA", "Vatican City");
		codes.put("VE", "Venezuela");
		codes.put("VN", "Vietnam");
		codes.put("WF", "Wallis & Futuna");
		codes.put("EH", "Western Sahara");
		codes.put("YE", "Yemen");
		codes.put("ZM", "Zambia");
		codes.put("ZW", "Zimbabwe");
		return codes;
	}

}
