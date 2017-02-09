/*
 *  Copyright (C) 2010-2012 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo Flow.
 *
 *  Akvo Flow is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Akvo Flow is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Akvo Flow.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.akvo.flow.domain;

import java.util.StringTokenizer;

import org.akvo.flow.exception.ValidationException;
import org.akvo.flow.util.ConstantUtil;

/**
 * data structure defining what rules should be used to validate question
 * responses
 * 
 * @author Christopher Fagiani
 */
public class ValidationRule {
    public static final int DEFAULT_MAX_LENGTH = 9999;

    private String validationType;
    private Integer maxLength;

    private Boolean allowSigned;
    private Boolean allowDecimal;

    private Double minVal;

    private Double maxVal;

    public ValidationRule(String type) {
        validationType = type;
        allowSigned = true;
        allowDecimal = true;
        minVal = null;
        maxVal = null;
        maxLength = DEFAULT_MAX_LENGTH;
    }

    public String getValidationType() {
        return validationType;
    }

    public void setValidationType(String validationType) {
        this.validationType = validationType;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Boolean getAllowSigned() {
        return allowSigned;
    }

    public void setAllowSigned(Boolean allowSigned) {
        this.allowSigned = allowSigned;
    }

    public Boolean getAllowDecimal() {
        return allowDecimal;
    }

    public void setAllowDecimal(Boolean allowDecimal) {
        this.allowDecimal = allowDecimal;
    }

    public void setAllowDecimal(String val) {
        if (val != null) {
            allowDecimal = new Boolean(val.trim());
        } else {
            allowDecimal = true;
        }
    }

    public void setAllowSigned(String val) {
        if (val != null) {
            allowSigned = new Boolean(val.trim());

        } else {
            allowSigned = true;
        }
    }

    public void setMaxLength(String val) {
        if (val != null) {
            maxLength = new Integer(val.trim());
        } else {
            maxLength = DEFAULT_MAX_LENGTH;
        }
    }

    public void setMinVal(Double minVal) {
        this.minVal = minVal;
    }

    public void setMaxVal(Double maxVal) {
        this.maxVal = maxVal;
    }

    public void setMinVal(String val) {
        if (val != null) {
            minVal = Double.parseDouble(val.trim());
        }
    }

    public void setMaxVal(String val) {
        if (val != null) {
            maxVal = Double.parseDouble(val.trim());
        }
    }

    public Double getMinVal() {
        return minVal;
    }

    public Double getMaxVal() {
        return maxVal;
    }

    public String getMinValString() {
        return convertValToString(minVal);
    }

    public String getMaxValString() {
        return convertValToString(maxVal);
    }

    /**
     * renders a string with or without a decimal point based on whether or not
     * this rule allows decimal values
     * 
     * @param num
     * @return
     */
    private String convertValToString(Double num) {
        String val = "";
        if (num != null) {
            if (allowDecimal) {
                val = num.toString();
            } else {
                val = "" + num.longValue();
            }
        }
        return val;
    }

    /**
     * validates the input, possibly transforming it according to validation
     * rules if the input cannot be transformed and does not conform to
     * validation rules, a ValidationException is thrown
     */
    public String performValidation(String val) throws ValidationException {
        String result = val;
        if (val != null) {
            if (ConstantUtil.NUMERIC_VALIDATION_TYPE
                    .equalsIgnoreCase(validationType)) {
                Double numVal = null;
                try {
                    numVal = new Double(val.trim());
                    if (minVal != null && minVal > numVal) {
                        throw new ValidationException("Value too small",
                                ValidationException.TOO_SMALL, null);
                    }
                    if (maxVal != null && maxVal < numVal) {
                        throw new ValidationException("Value too large",
                                ValidationException.TOO_LARGE, null);
                    }
                } catch (NumberFormatException e) {
                    throw new ValidationException("Value must be numeric",
                            ValidationException.INVALID_DATATYPE, e);
                }
            } else if (ConstantUtil.NAME_VALIDATION_TYPE
                    .equalsIgnoreCase(validationType)) {
                StringTokenizer strTok = new StringTokenizer(val, " ");
                StringBuilder builder = new StringBuilder();
                while (strTok.hasMoreTokens()) {
                    String word = strTok.nextToken();
                    builder.append(word.substring(0, 1).toUpperCase());
                    builder.append(word.substring(1));
                    if (strTok.hasMoreTokens()) {
                        builder.append(" ");
                    }
                }
                result = builder.toString();
            }
        }
        return result;
    }
}
