
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TratamentoArquivos implements Serializable{
    private byte[] dados;
    private String nomeArquivo;
    private String dirPai;
    private int tipo;
    private int tamanhoCaminho;
    
   //1 é arquivo regular  e 2 é diretório
    public TratamentoArquivos(String path, int tipo, int tamanhoCaminho){
        this.tipo = tipo;
        this.tamanhoCaminho = tamanhoCaminho;
        setNomeArquivo(path);
        if(tipo == 1){
            getArquivoDoDisk(path);
        }        
    }
   
    public void setNomeArquivo(String path){

        String[] auxPath = path.split("/");
        this.nomeArquivo = auxPath[auxPath.length-1];
        
        Path paths = Paths.get(path);   
        Path pai = paths.getParent();
        if(pai.toString().length() != path.length()){
            this.dirPai = pai.toString().substring(tamanhoCaminho);
        }else{
            this.dirPai = "";
        }
    }
    
    public int getTipo(){
        return this.tipo;
    }

   
    public String getNomeArquivo()
    {
        return this.nomeArquivo;
    }
    
    public String getNomeDirPai(){
        return this.dirPai;
    }
   
    public void getArquivoDoDisk(String path){
        try{
            dados = Files.readAllBytes(Paths.get(path));
        }catch(IOException e){
            System.out.println("Fail to read file " + path);
            System.err.format("IOException: %s %n", e);
        }
    }
    
    public void setArquivoNoDisk(String path){
        try {
            Files.write(Paths.get(path + "/"+nomeArquivo), dados);
        } catch (IOException ex) {
            Logger.getLogger(TratamentoArquivos.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void setDiretorios(String path){
        File p = new File(path + "/"+nomeArquivo);
        p.mkdir();    
    }
   

}
