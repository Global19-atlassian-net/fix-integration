/*
 * Copyright 2015 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.system_tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import quickfix.ConfigError;
import quickfix.SocketAcceptor;
import uk.co.real_logic.aeron.driver.MediaDriver;
import uk.co.real_logic.fix_gateway.FixGateway;
import uk.co.real_logic.fix_gateway.session.InitiatorSession;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.co.real_logic.fix_gateway.TestFixtures.unusedPort;
import static uk.co.real_logic.fix_gateway.session.SessionState.ACTIVE;
import static uk.co.real_logic.fix_gateway.system_tests.QuickFixUtil.*;
import static uk.co.real_logic.fix_gateway.system_tests.SystemTestUtil.*;

public class GatewayToQuickFixSystemTest
{

    private MediaDriver mediaDriver;
    private FixGateway initiatingGateway;
    private InitiatorSession initiatedSession;

    private FakeOtfAcceptor initiatingOtfAcceptor = new FakeOtfAcceptor();
    private FakeSessionHandler initiatingSessionHandler = new FakeSessionHandler(initiatingOtfAcceptor);

    private SocketAcceptor acceptor;
    private FakeQuickFixApplication acceptorApplication = new FakeQuickFixApplication();

    @Before
    public void launch() throws ConfigError
    {
        final int port = unusedPort();
        mediaDriver = launchMediaDriver();
        acceptor = launchQuickFixAcceptor(port, acceptorApplication);
        initiatingGateway = launchInitiatingGateway(initiatingSessionHandler);
        initiatedSession = initiate(initiatingGateway, port, INITIATOR_ID, ACCEPTOR_ID);
    }

    @Test
    public void sessionHasBeenInitiated()
    {
        assertTrue("Session has failed to connect", initiatedSession.isConnected());
        assertTrue("Session has failed to logon", initiatedSession.state() == ACTIVE);

        assertThat(acceptorApplication.logons(), containsInitiator());
    }

    @Test
    public void messagesCanBeSentFromInitiatorToAcceptor()
    {
        sendTestRequest(initiatedSession);

        assertQuickFixReceivedMessage(acceptorApplication);
    }

    @Test
    public void messagesCanBeSentFromAcceptorToInitiator()
    {
        sendTestRequestTo(onlySessionId(acceptor));

        assertReceivedMessage(initiatingSessionHandler, initiatingOtfAcceptor);
    }

    @Test
    public void initiatorSessionCanBeDisconnected()
    {
        initiatedSession.startLogout();

        assertQuickFixDisconnected(acceptorApplication, containsInitiator());
    }

    @Test
    public void acceptorSessionCanBeDisconnected()
    {
        logout(acceptor);

        assertDisconnected(initiatingSessionHandler, initiatedSession);
    }

    @After
    public void close() throws Exception
    {
        if (acceptor != null)
        {
            acceptor.stop();
        }

        if (initiatingGateway != null)
        {
            initiatingGateway.close();
        }

        if (mediaDriver != null)
        {
            mediaDriver.close();
        }
    }

}
