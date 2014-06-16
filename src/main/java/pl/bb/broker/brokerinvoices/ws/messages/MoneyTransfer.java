package pl.bb.broker.brokerinvoices.ws.messages;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.math.BigDecimal;

/**
 * Created with IntelliJ IDEA.
 * User: BamBalooon
 * Date: 13.06.14
 * Time: 17:15
 * To change this template use File | Settings | File Templates.
 */

@XmlRootElement
public class MoneyTransfer {
    private long id;
    private String description;
    private double value;

    @XmlElement
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement
    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}
