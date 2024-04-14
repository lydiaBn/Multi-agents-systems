import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class AuctionLauncher {
    public static void main(String[] args) {
        // Get a hold on JADE runtime
        Runtime rt = Runtime.instance();
        // Create a default profile
        Profile p1 = new ProfileImpl();
        // Create a new non-main container, connecting to the default
        // main container (i.e., on this host, port 1099)
        AgentContainer mainContainer = rt.createMainContainer(p1);

        try {
            // Start the auctioneer agent
            AgentController auctioneerController = mainContainer.createNewAgent("auctioneer", "Auctioneer", null);
            auctioneerController.start();

            // Start some bidder agents (you can adjust the number as needed)
            for (int i = 1; i <= 2; i++) {
                Object[] bidderArgs = new Object[] { auctioneerController }; // Pass the reference to ActionPerMinute
                AgentController bidderController = mainContainer.createNewAgent("bidder" + i, "BidderComp", bidderArgs);
                bidderController.start();
            }
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
    }
}
