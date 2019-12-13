
import com.google.gson.Gson;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.util.Util;

public class Server extends ReceiverAdapter {

    private File diretorioServer;
    private String path;
    final List<String> state = new LinkedList<String>();
    private Arquivos arquivosRecebidosServer;
    private JChannel channel;
    private int tamanho;

    public void await() {
        try {
            this.channel = new JChannel();
            this.channel.connect("Luziane");
            this.channel.setReceiver(this);
            this.channel.getState(null, 10000);
        } catch (Exception e) {
        }
    }

    public void receive(Message msg) {
        Arquivos arquivos = new Arquivos();
        arquivos = msg.getObject();
        Gson gson = new Gson();
        String mensagem = gson.toJson(arquivos);
        
        synchronized (state) {
            state.add(mensagem);
        }

        if (arquivos.getInicial().size() != 0 && arquivos.getInicial().size() != 1) {
            gravar(arquivos.getInicial());
        } else if (arquivos.getModificados().size() != 0) {
            gravar(arquivos.getModificados());
        } else if (arquivos.getDeletados().size() != 0) {
            for (int i = 0; i < arquivos.getDeletados().size(); i++) {
                diretorioServer = new File(path + "/" + arquivos.getDeletados().get(i));
                diretorioServer.delete();
            }
        }else{
            diretorioServer = new File(path + "/" + arquivos.getNomeCliente());
            System.out.println("Nome Cliente:"+arquivos.getNomeCliente());
            if(diretorioServer.exists()){
                System.out.println(diretorioServer.toString());
                raise(diretorioServer.toString());
            }
        }

        System.out.println("Terminou");
    }
    
    public void raise(String pathC){
        pegarArquivosClient(pathC);
        Message msg = new Message (null, arquivosRecebidosServer.getInicial()); //A null destination address sends the message to everyone in the cluster
        try {
            channel.send(msg);
        } catch (Exception ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void pegarArquivosClient(String pathC){
        tamanho = pathC.length();
        verifica(pathC);
    }
    
    public void verifica(String nome){
        arquivosRecebidosServer = new Arquivos();
        try (Stream<Path> walk = Files.walk(Paths.get(nome))) {
            List<String> resultArquivos = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());

            resultArquivos.forEach(System.out::println);

            for (int i = 0; i < resultArquivos.size(); i++) {
                arquivosRecebidosServer.getInicial().add(new TratamentoArquivos(resultArquivos.get(i), 1, tamanho));
            }
          
        } catch (IOException e) {
                e.printStackTrace();
        }
            
        try (Stream<Path> walk = Files.walk(Paths.get(nome))) {

            List<String> resultDiretorios = walk.filter(Files::isDirectory)
                            .map(x -> x.toString()).collect(Collectors.toList());
            resultDiretorios.forEach(System.out::println);

            for (int i = 0; i < resultDiretorios.size(); i++) {
                if(!nome.equals(resultDiretorios.get(i))){
                    arquivosRecebidosServer.getInicial().add(new TratamentoArquivos(resultDiretorios.get(i), 2, tamanho));
                }
            }
            
            for (int i = 0; i < arquivosRecebidosServer.getInicial().size(); i++) {
                System.out.println("Pai: "+arquivosRecebidosServer.getInicial().get(i).getNomeDirPai());
                System.out.println("Nome: "+arquivosRecebidosServer.getInicial().get(i).getNomeArquivo());
            }

        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }

    public void criarDiretorioLocal() {
        int cont = 1;

        while (true) {
            diretorioServer = new File("/home/luziane/Documentos/Server" + cont);

            if (!diretorioServer.exists()) {
                diretorioServer.mkdir();
                path = diretorioServer.getAbsolutePath();
                break;
            } else {
                cont++;
            }
        }

    }

    public void gravar(ArrayList<TratamentoArquivos> recebidos) {

        do {
            for (int i = 0; i < recebidos.size(); i++) {
                if (recebidos.get(i).getTipo() == 1) {
                    diretorioServer = new File(path + recebidos.get(i).getNomeDirPai());
                    if (diretorioServer.exists()) {
                        recebidos.get(i).setArquivoNoDisk(path + recebidos.get(i).getNomeDirPai());
                        recebidos.remove(i);
                    }
                } else {
                    diretorioServer = new File(path + recebidos.get(i).getNomeDirPai());
                    if (diretorioServer.exists()) {
                        recebidos.get(i).setDiretorios(path + recebidos.get(i).getNomeDirPai());
                        recebidos.remove(i);
                    }
                }
            }
        } while (!recebidos.isEmpty());

    }

    public void getState(OutputStream output) throws Exception {
        synchronized (state) {
            Util.objectToStream(state, new DataOutputStream(output));
        }
    }

    @SuppressWarnings("unchecked")
    public void setState(InputStream input) throws Exception {
        List<String> list = (List<String>) Util.objectFromStream(new DataInputStream(input));
        synchronized (state) {
            state.clear();
            state.addAll(list);
        }
        System.out.println("received state (" + list.size() + " messages in chat history):");
        Gson gson = new Gson();

        for (int i = 0; i < list.size(); i++) {
            Arquivos msgs = gson.fromJson(list.get(i), Arquivos.class);
            if (msgs.getInicial().size() != 0) {
                gravar(msgs.getInicial());
            } else if (msgs.getModificados().size() != 0) {
                gravar(msgs.getModificados());
            } else if (msgs.getDeletados().size() != 0) {
                for (int j = 0; j < msgs.getDeletados().size(); j++) {
                    diretorioServer = new File(path + msgs.getDeletados().get(i));
                    diretorioServer.delete();
                }
            }
        }

    }

    public static void main(String[] args) {
        Server server = new Server();
        server.criarDiretorioLocal();
        server.await();

    }

}
