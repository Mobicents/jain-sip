package examples.subsnotify;

import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.util.ArrayList;
import java.util.Properties;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.Transaction;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import junit.framework.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is a Subscriber template. Shootist is the guy that shoots and
 * shootme is the guy that gets shot.
 *
 * @author M. Ranganathan
 */

public class Subscriber implements SipListener {

    private static final Logger LOG = LogManager.getLogger(Subscriber.class);

    private SipProvider sipProvider;

    private static AddressFactory addressFactory;

    private static MessageFactory messageFactory;

    private static HeaderFactory headerFactory;

    private static SipStack sipStack;

    private ContactHeader contactHeader;

    private static String notifierPort;

    private static String transport;

    private int count;

    private Dialog subscriberDialog;

    private Dialog forkedDialog;

    private ClientTransaction subscribeTid;

    private ListeningPoint listeningPoint;

    protected static final String usageString = "java "
            + "examples.subsnotify.Subscriber \n"
            + ">>>> is your class path set to the root?";

    private static void usage() {
        LOG.info(usageString);
        System.exit(0);

    }

    public void processRequest(RequestEvent requestReceivedEvent) {
        Request request = requestReceivedEvent.getRequest();
        ServerTransaction serverTransactionId = requestReceivedEvent
                .getServerTransaction();
        String viaBranch = ((ViaHeader)(request.getHeaders(ViaHeader.NAME).next())).getParameter("branch");

        LOG.info("\n\nRequest " + request.getMethod() + " received at "
                + sipStack.getStackName() + " with server transaction id "
                + serverTransactionId +
                " branch ID = " + viaBranch);

        if (request.getMethod().equals(Request.NOTIFY))
            processNotify(requestReceivedEvent, serverTransactionId);

    }

    public synchronized void processNotify(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {
        SipProvider provider = (SipProvider) requestEvent.getSource();
        Request notify = requestEvent.getRequest();
        try {
            LOG.info("subscriber:  got a notify count  " + this.count++ );
            if (serverTransactionId == null) {
                LOG.info("subscriber:  null TID.");
                serverTransactionId = provider.getNewServerTransaction(notify);
            }
            Dialog dialog = serverTransactionId.getDialog();
            LOG.info("Dialog = " + dialog);

            if (dialog != null) {
                LOG.info("Dialog State = " + dialog.getState());
            }

            if ( dialog != subscriberDialog ) {
                if (forkedDialog == null) {
                    forkedDialog = dialog;
                } else  {
                    if (forkedDialog != dialog)  {
                        System.out.println("dialog = " + dialog );
                        System.out.println("forkedDialog " + forkedDialog);
                        System.out.println("subscribedialog = " + this.subscriberDialog);
                        ((SIPTransactionStack)sipStack).printDialogTable();
                    }
                    TestCase.assertTrue("Dialog should be either the subscriber dialog ",
                            forkedDialog  == dialog);
                }
            }

            Response response = messageFactory.createResponse(200, notify);
            // SHOULD add a Contact
            ContactHeader contact = (ContactHeader) contactHeader.clone();
            ((SipURI)contact.getAddress().getURI()).setParameter( "id", "sub" );
            response.addHeader( contact );
            LOG.info("Transaction State = " + serverTransactionId.getState());
            serverTransactionId.sendResponse(response);
            if (dialog != null ) {
                LOG.info("Dialog State = " + dialog.getState());
            }
            SubscriptionStateHeader subscriptionState = (SubscriptionStateHeader) notify
                    .getHeader(SubscriptionStateHeader.NAME);

            // Subscription is terminated?
            String state = subscriptionState.getState();
            if (state.equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
                dialog.delete();
            } else {
                LOG.info("Subscriber: state now " + state);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error("Unexpected exception", ex);
            System.exit(0);

        }
    }

    public void processResponse(ResponseEvent responseReceivedEvent) {
        LOG.info("Got a response");
        Response response = (Response) responseReceivedEvent.getResponse();
        Transaction tid = responseReceivedEvent.getClientTransaction();

        LOG.info("Response received with client transaction id " + tid
                + ":\n" + response.getStatusCode()  );
        if (tid == null) {
            LOG.info("Stray response -- dropping ");
            return;
        }
        LOG.info("transaction state is " + tid.getState());
        LOG.info("Dialog = " + tid.getDialog());
        if ( tid.getDialog () != null )
        LOG.info("Dialog State is " + tid.getDialog().getState());

    }

    public void createProvider() throws Exception {

        this.listeningPoint = sipStack.createListeningPoint("127.0.0.1", 5060,
                transport);
        sipProvider = sipStack.createSipProvider(listeningPoint);

    }

    public void sendSubscribe() {

        try {

            String fromName = "BigGuy";
            String fromSipAddress = "here.com";
            String fromDisplayName = "The Master Blaster";

            String toSipAddress = "there.com";
            String toUser = "LittleGuy";
            String toDisplayName = "The Little Blister";

            // create >From Header
            SipURI fromAddress = addressFactory.createSipURI(fromName,
                    fromSipAddress);

            Address fromNameAddress = addressFactory.createAddress(fromAddress);
            fromNameAddress.setDisplayName(fromDisplayName);
            FromHeader fromHeader = headerFactory.createFromHeader(
                    fromNameAddress, "12345");

            // create To Header
            SipURI toAddress = addressFactory
                    .createSipURI(toUser, toSipAddress);
            Address toNameAddress = addressFactory.createAddress(toAddress);
            toNameAddress.setDisplayName(toDisplayName);
            ToHeader toHeader = headerFactory.createToHeader(toNameAddress,
                    null);

            // create Request URI
            SipURI requestURI = addressFactory.createSipURI(toUser,
                    toSipAddress);

            // Create ViaHeaders

            ArrayList viaHeaders = new ArrayList();
            int port = sipProvider.getListeningPoint(transport).getPort();
            ViaHeader viaHeader = headerFactory.createViaHeader("127.0.0.1",
                    port, transport, null);

            // add via headers
            viaHeaders.add(viaHeader);

            // Create a new CallId header
            CallIdHeader callIdHeader = sipProvider.getNewCallId();

            // Create a new Cseq header
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L,
                    Request.SUBSCRIBE);

            // Create a new MaxForwardsHeader
            MaxForwardsHeader maxForwards = headerFactory
                    .createMaxForwardsHeader(70);

            // Create the request.
            Request request = messageFactory.createRequest(requestURI,
                    Request.SUBSCRIBE, callIdHeader, cSeqHeader, fromHeader,
                    toHeader, viaHeaders, maxForwards);
            // Create contact headers
            String host = listeningPoint.getIPAddress();

            SipURI contactUrl = addressFactory.createSipURI(fromName, host);
            contactUrl.setPort(listeningPoint.getPort());

            // Create the contact name address.
            SipURI contactURI = addressFactory.createSipURI(fromName, host);
            contactURI.setTransportParam(transport);
            contactURI.setPort(sipProvider.getListeningPoint(transport).getPort());

            Address contactAddress = addressFactory.createAddress(contactURI);

            // Add the contact address.
            contactAddress.setDisplayName(fromName);

            contactHeader = headerFactory.createContactHeader(contactAddress);
            request.addHeader(contactHeader);

            // JvB: To test forked SUBSCRIBEs, send it via the Forker
            // Note: BIG Gotcha: Need to do this before creating the
            // ClientTransaction!

            RouteHeader route = headerFactory.createRouteHeader(addressFactory
                    .createAddress("<sip:127.0.0.1:" + notifierPort
                            + ";transport=" + transport + ";lr>"));
            request.addHeader(route);
            // JvB end added

            // Create the client transaction.
            subscribeTid = sipProvider.getNewClientTransaction(request);

            // Create an event header for the subscription.
            EventHeader eventHeader = headerFactory.createEventHeader("foo");
            eventHeader.setEventId("foo");
            request.addHeader(eventHeader);

            LOG.info("Subscribe Dialog = " + subscribeTid.getDialog());

            this.subscriberDialog = subscribeTid.getDialog();
            // send the request out.
            subscribeTid.sendRequest();


        } catch (Throwable ex) {
            LOG.info(ex.getMessage());
            ex.printStackTrace();
            usage();
        }
    }

    public static void main(String args[]) throws Exception {
        // 5065 sends to the forker.
        // 5070 sends to the subscriber1

        notifierPort = args[0];

        transport = "udp";

        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        Properties properties = new Properties();

        properties.setProperty("javax.sip.USE_ROUTER_FOR_ALL_URIS", "false");

        properties.setProperty("javax.sip.STACK_NAME", "subscriber");
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "subscriberdebug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "subscriberlog.txt");

        properties.setProperty("javax.sip.FORKABLE_EVENTS", "foo");

        // Set to 0 in your production code for max speed.
        // You need 16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");

        sipStack = sipFactory.createSipStack(properties);
        LOG.info("createSipStack " + sipStack);
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();
        messageFactory = sipFactory.createMessageFactory();

        Subscriber subscriber = new Subscriber();
        subscriber.createProvider();
        subscriber.sipProvider.addSipListener(subscriber);
        subscriber.sendSubscribe();

    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        LOG.info("io exception event recieved");
    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {

    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        LOG.info("dialog terminated event recieved");
    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {

        LOG.info("Transaction Time out");
    }
}
