/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.schema;

import static org.testng.AssertJUnit.assertTrue;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.PropertyPath;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.CredentialsType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PasswordType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ProtectedStringType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserType;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import javax.xml.bind.JAXBException;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author Radovan Semancik
 */
public class TestDeltaConverter {
	
	private static final File TEST_DIR = new File("src/test/resources/deltaconverter");
	private static final File COMMON_TEST_DIR = new File("src/test/resources/common");
	
	private static final PropertyPath CREDENTIALS_PASSWORD_PROTECTED_STRING_PATH = 
		new PropertyPath(UserType.F_CREDENTIALS, CredentialsType.F_PASSWORD, PasswordType.F_PROTECTED_STRING);

    @BeforeSuite
    public void setup() throws SchemaException, SAXException, IOException {
        DebugUtil.setDefaultNamespacePrefix(MidPointConstants.NS_MIDPOINT_PUBLIC_PREFIX);
        PrismTestUtil.resetPrismContext(MidPointPrismContextFactory.FACTORY);
    }

    @Test
    public void testRefWithObject() throws SchemaException, FileNotFoundException, JAXBException {
    	System.out.println("===[ testRefWithObject ]====");
    	
    	ObjectModificationType objectChange = PrismTestUtil.unmarshalObject(new File(TEST_DIR, "user-modify-add-account.xml"),
    			ObjectModificationType.class);
    	
    	ObjectDelta<UserType> objectDelta = DeltaConvertor.createObjectDelta(objectChange, UserType.class, 
    			PrismTestUtil.getPrismContext());
    	
    	assertNotNull("No object delta", objectDelta);
    	objectDelta.checkConsistence();
    	assertEquals("Wrong OID", "c0c010c0-d34d-b33f-f00d-111111111111", objectDelta.getOid());
    	ReferenceDelta accoutRefDelta = objectDelta.findReferenceModification(UserType.F_ACCOUNT_REF);
    	assertNotNull("No accountRef delta", accoutRefDelta);
    	Collection<PrismReferenceValue> valuesToAdd = accoutRefDelta.getValuesToAdd();
    	assertEquals("Wrong number of values to add", 1, valuesToAdd.size());
    	PrismReferenceValue accountRefVal = valuesToAdd.iterator().next();
    	assertNotNull("No object in accountRef value", accountRefVal.getObject());
    	
    	objectDelta.assertDefinitions();
    }
    
    @Test
    public void testPasswordChange() throws SchemaException, FileNotFoundException, JAXBException {
    	System.out.println("===[ testPasswordChange ]====");
    	
    	ObjectModificationType objectChange = PrismTestUtil.unmarshalObject(new File(TEST_DIR, "user-modify-password.xml"),
    			ObjectModificationType.class);
    	
    	// WHEN
    	ObjectDelta<UserType> objectDelta = DeltaConvertor.createObjectDelta(objectChange, UserType.class, 
    			PrismTestUtil.getPrismContext());
    	
    	// THEN
    	assertNotNull("No object delta", objectDelta);
    	objectDelta.checkConsistence();
    	assertEquals("Wrong OID", "c0c010c0-d34d-b33f-f00d-111111111111", objectDelta.getOid());
    	PropertyDelta<ProtectedStringType> protectedStringDelta = objectDelta.findPropertyDelta(CREDENTIALS_PASSWORD_PROTECTED_STRING_PATH);
    	assertNotNull("No protectedString delta", protectedStringDelta);
    	Collection<PrismPropertyValue<ProtectedStringType>> valuesToReplace = protectedStringDelta.getValuesToReplace();
    	assertEquals("Wrong number of values to add", 1, valuesToReplace.size());
    	PrismPropertyValue<ProtectedStringType> protectedStringVal = valuesToReplace.iterator().next();
    	assertNotNull("Null value in protectedStringDelta", protectedStringVal);
    	
    	PrismObject<UserType> user = PrismTestUtil.parseObject(new File(COMMON_TEST_DIR, "user-jack.xml"));
    	// apply to user
    	objectDelta.applyTo(user);
    	
    	PrismProperty<ProtectedStringType> protectedStringProperty = user.findProperty(CREDENTIALS_PASSWORD_PROTECTED_STRING_PATH);
    	PrismPropertyValue<ProtectedStringType> protectedStringPropertyValue = protectedStringProperty.getValue();
    	assertTrue("protectedString not equivalent", protectedStringPropertyValue.equalsRealValue(protectedStringVal));
    	
    	objectDelta.assertDefinitions();
    }
    
    @Test
    public void testAddAssignment() throws SchemaException, FileNotFoundException, JAXBException {
    	System.out.println("===[ testAddAssignment ]====");
    	
    	ObjectModificationType objectChange = PrismTestUtil.unmarshalObject(new File(TEST_DIR, "user-modify-add-role-pirate.xml"),
    			ObjectModificationType.class);
    	
    	// WHEN
    	ObjectDelta<UserType> objectDelta = DeltaConvertor.createObjectDelta(objectChange, UserType.class, 
    			PrismTestUtil.getPrismContext());
    	
    	System.out.println("Delta:");
    	System.out.println(objectDelta.dump());
    	
    	// THEN
    	assertNotNull("No object delta", objectDelta);
    	objectDelta.checkConsistence();
    	assertEquals("Wrong OID", "c0c010c0-d34d-b33f-f00d-111111111111", objectDelta.getOid());
    	ContainerDelta<AssignmentType> assignmentDelta = objectDelta.findContainerDelta(UserType.F_ASSIGNMENT);
    	assertNotNull("No assignment delta", assignmentDelta);
    	Collection<PrismContainerValue<AssignmentType>> valuesToAdd = assignmentDelta.getValuesToAdd();
    	assertEquals("Wrong number of values to add", 1, valuesToAdd.size());
    	PrismContainerValue<AssignmentType> assignmentVal = valuesToAdd.iterator().next();
    	assertNotNull("Null value in protectedStringDelta", assignmentVal);
    	
    	PrismReference targetRef = assignmentVal.findReference(AssignmentType.F_TARGET_REF);
    	assertNotNull("No targetRef in assignment", targetRef);
    	PrismReferenceValue targetRefVal = targetRef.getValue();
    	assertNotNull("No targetRef value in assignment", targetRefVal);
    	assertEquals("Wrong OID in targetRef value", "12345678-d34d-b33f-f00d-987987987988", targetRefVal.getOid());
    	assertEquals("Wrong type in targetRef value", RoleType.COMPLEX_TYPE, targetRefVal.getTargetType());
    	
    	PrismObject<UserType> user = PrismTestUtil.parseObject(new File(COMMON_TEST_DIR, "user-jack.xml"));
    	
    	objectDelta.assertDefinitions("delta before test");
    	user.assertDefinitions("user before test");
    	
    	// apply to user
    	objectDelta.applyTo(user);
    	
    	objectDelta.assertDefinitions("delta after test");
    	user.assertDefinitions("user after test");

    	// TODO
    }
}
