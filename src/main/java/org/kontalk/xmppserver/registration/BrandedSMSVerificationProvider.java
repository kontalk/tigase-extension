/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.xmppserver.registration;

import tigase.conf.ConfigurationException;
import tigase.db.TigaseDBException;

import java.util.Map;

/**
 * Base class for a branded SMS verification provider.
 * @author Daniele Ricci
 */
public abstract class BrandedSMSVerificationProvider extends AbstractSMSVerificationProvider {

    protected String brandImageVector;
    protected String brandImageSmall;
    protected String brandImageMedium;
    protected String brandImageLarge;
    protected String brandImageHighDef;
    protected String brandLink;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException, ConfigurationException {
        super.init(settings);
        brandImageVector = (String) settings.get("brand-image-vector");
        brandImageSmall = (String) settings.get("brand-image-small");
        brandImageMedium = (String) settings.get("brand-image-medium");
        brandImageLarge = (String) settings.get("brand-image-large");
        brandImageHighDef = (String) settings.get("brand-image-hd");
        brandLink = (String) settings.get("brand-link");
    }

    @Override
    public String getBrandImageVector() {
        return brandImageVector;
    }

    @Override
    public String getBrandImageSmall() {
        return brandImageSmall;
    }

    @Override
    public String getBrandImageMedium() {
        return brandImageMedium;
    }

    @Override
    public String getBrandImageLarge() {
        return brandImageLarge;
    }

    @Override
    public String getBrandImageHighDef() {
        return brandImageHighDef;
    }

    @Override
    public String getBrandLink() {
        return brandLink;
    }

}
