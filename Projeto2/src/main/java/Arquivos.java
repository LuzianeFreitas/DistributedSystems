
import java.io.Serializable;
import java.util.ArrayList;


public class Arquivos implements Serializable{
    private ArrayList<TratamentoArquivos> inicial;
    private ArrayList<String> deletados;
    private ArrayList<TratamentoArquivos> modificados;
    private String  nomeCliente;

    

    public Arquivos() {
        this.inicial = new ArrayList();
        this.deletados = new ArrayList();
        this.modificados = new ArrayList();
        this.nomeCliente = "";
    }
    
    public String getNomeCliente() {
        return nomeCliente;
    }

    public void setNomeCliente(String nomeCliente) {
        this.nomeCliente = nomeCliente;
    }
    

    public ArrayList<TratamentoArquivos> getInicial() {
        return inicial;
    }

    public ArrayList<String> getDeletados() {
        return deletados;
    }

    public ArrayList<TratamentoArquivos> getModificados() {
        return modificados;
    }
    
    
    
}
