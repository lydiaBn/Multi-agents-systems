import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

/**
 * JADE agent representing an auctioneer of an auction.
 * It has single sequential behavior representing its lifecycle.
 */
public class Auctioneer extends Agent {

    // The catalogue of items for sale (maps the title of a item to its price)
    private Hashtable<String, ItemInfo> catalogue;

    // Highest bid received
    private int highestBid = 0;

    // The number of rounds the auction should go through before terminating
    private int maxRounds = 3; // Change this to set the desired number of rounds
    private int currentRound = 0;


    // Flag to indicate if bidding is active
    private boolean biddingActive = false;

    // The GUI by means of which the user can add items in the catalogue
    private AuctioneerGUI myGui;

    // Show whether auction has started
    private boolean auctionStarted = false;

    // The template to receive replies
    public MessageTemplate mt;

    // The list of known bidders
    public AID[] bidders;

    // The bidder who provides the best offer
    public AID bestBidder;

    // The most updated best offered price
    public int bestPrice;

    public boolean biddersFound = false;
    public boolean CFPSent = false;
    public boolean bidsReceived = false;

    public FindBidder p = null;
    public SendCFP q = null;
    public ReceiveBids r = null;
    public AnnounceWinnerAndUpdateCatalogue s = null;

    // Define a new message type for informing bidders about the highest bid
    public static final int HIGHEST_BID = 3;

    public Hashtable<String, ItemInfo> getCatalogue() {
        return catalogue;
    }

    public static class ItemInfo {
        private int initialPrice;
        private int reservePrice;

        public ItemInfo(int initialPrice, int reservePrice) {
            this.initialPrice = initialPrice;
            this.reservePrice = reservePrice;
        }

        public int getInitialPrice() {
            return initialPrice;
        }

        public int getReservePrice() {
            return reservePrice;
        }
    }

    @Override
    protected void setup() {

        // Printout a welcome message
        System.out.println("Hello! Auctioneer " + getAID().getName() + " is ready");

        // Create the catalogue
        catalogue = new Hashtable<>();
        updateCatalogue("Antique Vase", 1200, 1250);

        // Create and show the GUI
        myGui = new AuctioneerGUI(this);
        myGui.showGui();

        // Add a TickerBehaviour that schedules a request to bidders every minute
        addBehaviour(new ActionPerMinute(this));

        // Add behavior to receive highest bid from bidders
        addBehaviour(new ReceiveHighestBid(this));
    }

    // Put agent clean-up operations here
    protected void takeDown() {
        // Close the GUI
        myGui.dispose();

        // Printout a dismissal message
        System.out.println("Auctioneer " + getAID().getName() + " terminating");
    }

    /**
     * This is invoked by the GUI when the user adds a new item for sale
     */
    public void updateCatalogue(final String title, final int price, final int rprice) {

        addBehaviour(new OneShotBehaviour() {
            public void action() {
                catalogue.put(title, new ItemInfo(price, rprice)); // Store both initial price and reserve price
                System.out.println(title + " is inserted into catalogue. Initial Price = " + price + ", Reserve Price = " + rprice);
            }
        });
    }

    public Integer removeItemFromCatalogue(final String title) {
        ItemInfo itemInfo = (ItemInfo) catalogue.get(title);
        Integer price = null;
        if (itemInfo != null) {
            price = itemInfo.getInitialPrice();
            addBehaviour(
                    new OneShotBehaviour() {
                        public void action() {
                            catalogue.remove(title);
                        }
                    }
            );
        }
        return price;
    }
    public boolean isCatalogueEmpty() {
        return catalogue.isEmpty();
    }

    public String getFirstItemName() {
        return catalogue.keySet().iterator().next();
    }

    public int getItemInitialPrice(final String title) {
        ItemInfo itemInfo = catalogue.get(title);
        if (itemInfo != null) {
            return itemInfo.getInitialPrice();
        } else {
            return 0;
        }
    }

    // Add a TickerBehaviour that schedules an auction to bidders every minute
    class ActionPerMinute extends TickerBehaviour {

        private Auctioneer myAgent;
        private String currentItemName;
        private int rounds = 0;
        private int maxRounds = 3; // Change this to set the desired number of rounds

        public ActionPerMinute(Auctioneer agent) {
            super(agent, 10000);
            myAgent = agent;
        }

        @Override
        protected void onTick() {

            // Check if maxRounds has been reached
            if (rounds >= maxRounds) {
                System.out.println("Auction has completed " + maxRounds + " rounds for item " + currentItemName + ". Moving to next item.");
                rounds = 0; // Reset rounds for the next item
                // Here you should add code to move to the next item in the catalogue
                return;
            }

            // Initialize all conditions
            myAgent.biddersFound = false;
            myAgent.CFPSent = false;
            myAgent.bidsReceived = false;

            // If there is any item to sell
            if (!myAgent.isCatalogueEmpty()) {

                if (myAgent.p != null) myAgent.removeBehaviour(myAgent.p);
                if (myAgent.q != null) myAgent.removeBehaviour(myAgent.q);
                if (myAgent.r != null) myAgent.removeBehaviour(myAgent.r);
                if (myAgent.s != null) myAgent.removeBehaviour(myAgent.s);

                currentItemName = myAgent.getFirstItemName();
                System.out.println("Starting auction for item " + currentItemName);

                System.out.println("Waiting for bidders.. ");

                // Retrieve the reserve price for the current item
                Hashtable<String, ItemInfo> catalogue = myAgent.getCatalogue();
                ItemInfo itemInfo = catalogue.get(currentItemName);
                int reservePrice = itemInfo.getReservePrice();

                // Find Bidder
                myAgent.p = new FindBidder(myAgent);
                myAgent.addBehaviour(myAgent.p);

                // Send CFP to all bidders
                myAgent.q = new SendCFP(myAgent, currentItemName, myAgent.getItemInitialPrice(currentItemName));
                myAgent.addBehaviour(myAgent.q);

                // Receive all proposals/refusals from bidders and find the highest bidder
                myAgent.r = new ReceiveBids(myAgent);
                myAgent.addBehaviour(myAgent.r);

                // Send the request order to the bidder that provided the best offer
                myAgent.s = new AnnounceWinnerAndUpdateCatalogue(myAgent, currentItemName, reservePrice);
                myAgent.addBehaviour(myAgent.s);

                rounds++; // Increment rounds at the end of each auction round
            } else {
                System.out.println("Please add an item before we can commence auctions");
            }
        }
    }




    class FindBidder extends Behaviour {

        private Auctioneer myAgent;

        public FindBidder(Auctioneer agent) {
            super(agent);
            myAgent = agent;
        }

        public void action() {

            if (!myAgent.biddersFound) {

                // Update the list of bidders
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("blind-auction");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    if (result.length > 0) {
                        System.out.println("Found the following " + result.length + " bidders:");
                        myAgent.bidders = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            myAgent.bidders[i] = result[i].getName();
                            System.out.println(myAgent.bidders[i].getName());
                        }
                        myAgent.biddersFound = true;
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }

            }
        }

        public boolean done() {
            return myAgent.biddersFound;
        }
    }

    /**
     * Send CFP to all bidders
     */
    class SendCFP extends Behaviour {

        private Auctioneer myAgent;
        private String itemName;
        private int itemInitialPrice;

        public SendCFP(Auctioneer agent, String itemName, int itemInitialPrice) {
            super(agent);
            myAgent = agent;
            this.itemName = itemName;
            this.itemInitialPrice = itemInitialPrice;
        }

        public void action() {

            if (!myAgent.CFPSent && myAgent.biddersFound) {

                // Send the cfp to all bidders
                System.out.println("Sending CFP to all bidders..");
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                for (int i = 0; i < myAgent.bidders.length; ++i) {
                    cfp.addReceiver(myAgent.bidders[i]);
                }
                cfp.setContent(this.itemName + "," + this.itemInitialPrice);
                cfp.setConversationId("blind-bid");
                cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value
                myAgent.send(cfp);

                // Prepare message template
                myAgent.mt = MessageTemplate.and(MessageTemplate.MatchConversationId("blind-bid"),
                        MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

                myAgent.CFPSent = true;

            }
        }

        public boolean done() {
            return myAgent.CFPSent;
        }
    }

    /**
     * Receive all proposals/refusals from bidders and find the highest bidder
     */
    class ReceiveBids extends Behaviour {

        private Auctioneer myAgent;

        private int repliesCnt = 0; // The counter of replies from seller agents

        public ReceiveBids(Auctioneer agent) {
            super(agent);
            myAgent = agent;
        }

        public void action() {

            if (myAgent.CFPSent) {

                // Receive all proposals/refusals from seller agents
                ACLMessage msg = myAgent.receive(myAgent.mt);
                if (msg != null) {
                    // Bid received
                    if (msg.getPerformative() == ACLMessage.PROPOSE) {

                        // This is an offer
                        int price = Integer.parseInt(msg.getContent());
                        if (myAgent.bestBidder == null || price > myAgent.bestPrice) {
                            // This is the best offer at present
                            myAgent.bestPrice = price;
                            myAgent.bestBidder = msg.getSender();
                            // Print the highest bid and bidder
                            System.out.println("New highest bid: " + myAgent.bestPrice + " from bidder: " + myAgent.bestBidder.getLocalName());
                        }

                        // Inform the bidder that the bid is received
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent("Your bid is received");
                        myAgent.send(reply);
                    }

                    if (msg.getPerformative() == ACLMessage.REFUSE) {
                        System.out.println(msg.getSender().getLocalName() + " is not joining this auction");
                    }

                    repliesCnt++;
                } else {
                    block();
                }

                if (repliesCnt >= myAgent.bidders.length) {
                    // We have received all bids
                    myAgent.bidsReceived = true;
                }

            }
        }

        public boolean done() {
            return myAgent.bidsReceived;
        }
    }

    /**
     * Send the request order to the bidder that provided the best offer
     *
     * @condition: if there is any winner
     */
    class AnnounceWinnerAndUpdateCatalogue extends Behaviour {

        private Auctioneer myAgent;

        private String itemName;
        private int reservePrice; // Reserve price for the item

        private boolean isDone = false;

        public AnnounceWinnerAndUpdateCatalogue(Auctioneer agent, String itemName, int reservePrice) {
            this.itemName = itemName;
            myAgent = agent;
            this.reservePrice = reservePrice;
        }

        public void action() {
            if (myAgent.bidsReceived) {
                if (myAgent.bestPrice >= myAgent.getItemInitialPrice(this.itemName) && myAgent.bestPrice >= this.reservePrice) {
                    // Send the purchase order to the seller that provided the best offer
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(myAgent.bestBidder);
                    order.setContent(this.itemName + "," + myAgent.bestPrice);
                    order.setConversationId("blind-bid");
                    order.setReplyWith("order" + System.currentTimeMillis());

                    System.out.println("Announcing Winner for " + this.itemName);

                    Integer price = (Integer) myAgent.removeItemFromCatalogue(this.itemName);
                    if (price != null) {
                        System.out.println(itemName + " sold to agent " + myAgent.bestBidder.getName());
                    } else {
                        // The requested item has been sold to another buyer..somehow
                        order.setPerformative(ACLMessage.FAILURE);
                        order.setContent("not-available");
                        System.out.println("the item " + itemName + " cannot be sold as the best bidding price is not sufficient..");
                    }
                    myAgent.send(order);

                    // Re-Initialize all conditions
                    myAgent.biddersFound = false;
                    myAgent.CFPSent = false;
                    myAgent.bidsReceived = false;
                    myAgent.bestPrice = 0;
                    myAgent.bestBidder = null;
                } else {
                    System.out.println("No winner. Bids were insufficient or didn't meet the reserve price.");
                }
                isDone = true;
            }
        }

        public boolean done() {
            return isDone;
        }
    }

    /**
     * Receive the highest bid from bidders
     */
    class ReceiveHighestBid extends CyclicBehaviour {

        private Auctioneer myAgent;

        public ReceiveHighestBid(Auctioneer agent) {
            super(agent);
            myAgent = agent;
        }

        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(HIGHEST_BID));
            if (msg != null) {
                // Update the highest bid received
                int bid = Integer.parseInt(msg.getContent());
                if (bid > myAgent.highestBid) {
                    myAgent.highestBid = bid;
                    System.out.println("Received new highest bid: " + bid);
                }
            } else {
                block();
            }
        }
    }
}
