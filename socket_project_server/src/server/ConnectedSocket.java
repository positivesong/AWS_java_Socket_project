package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import lombok.RequiredArgsConstructor;
import server.dto.RequestBodyDto;
import server.dto.SendMessage;
import server.entity.Room;

@RequiredArgsConstructor
public class ConnectedSocket extends Thread {
	private Gson gson;
	private final Socket socket;
	private String username;
	private boolean isOwner = false;  // 송유나 방장 변수추가
	@Override
	public void run() {

		ServerMain serverMain = ServerMain.getInstance();
		gson = new Gson();

		//예외처리, 나중에 수정 필요
		while (true) {
			try {
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String requestBody = null;
				
				try {
					requestBody = bufferedReader.readLine();
					requestController(requestBody);
				}catch (SocketException e) {
					//serverMain.dispose();
					//System.exit(0);
				} catch (NullPointerException e) {
					e.printStackTrace();
					serverMain.getInstance().sysoutGUI("널포인터 익셉션 at readLine");
				} catch (Exception e) {
					e.printStackTrace();
				} 
			} catch (IOException e) {
				e.printStackTrace();
			} catch (java.lang.NullPointerException e) {
				e.printStackTrace();
				System.out.println("널포인터익셉션 at BufferedReader");
				
			}
		}
	}

	// <<< Resource에 따른 case 설정 >>>
	private void requestController(String requestBody) {
		String resource = gson.fromJson(requestBody, RequestBodyDto.class).getResource();

		// RequestBodyDto<?> requestBodyDto = gson.fromJson(requestBody,
		// RequestBodyDto.class);
		/*
		 * TypeToken<RequestBodyDto<SendMessage>> token = new
		 * TypeToken<RequestBodyDto<SendMessage>>( ) { }; RequestBodyDto<SendMessage>
		 * requestBodyDto2 = gson.fromJson(requestBody, token.getType());
		 */

		switch (resource) {
		case "connection":
			connection(requestBody);
			break;

		case "createRoom":
			createRoom(requestBody);
			break;
			
		case "join": 
			join(requestBody);
			break;
		
		case "exitRoom":
			exitRoom(requestBody);
			break;
			
		case "ownerExitRoom" :
			ownerExitRoom(requestBody);
			break;
		case "sendWhisper":
			sendMessage(requestBody);
			break;
			
		case "sendMessage": // 밑에 내용 개중요함. 모든 케이스를 다 적어야함.
			sendMessage(requestBody);
			break;
			
		
		}
	}
	
	// <<< 위 case에 따른 행동 정의>>>
	
	//연결되었을때 대기실의 룸리스트 반환
	private void connection(String requestBody) {
		username = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
		
		List<String> roomNameList = new ArrayList<>();

		server.ServerMain.roomList.forEach(room -> {
			roomNameList.add(room.getRoomName());
		});

		RequestBodyDto<List<String>> updateRoomListRequestBodyDto =
				new RequestBodyDto<List<String>>("updateRoomList", roomNameList);

		ServerSender.getInstance().send(socket, updateRoomListRequestBodyDto);
		
	}
	
    public String getUsername() {
        return username;
    }
	//방 만들었을 때 룸리스트 반환
	private void createRoom(String requestBody) {
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();

		
		Room newRoom = Room.builder()
				.roomName(roomName)
				.owner(username)
				.userList(new ArrayList<ConnectedSocket>())
				.build();

		

		List<String> roomNameList = new ArrayList<>();

		server.ServerMain.roomList.forEach(room -> {
			roomNameList.add(room.getRoomName());
		});

		RequestBodyDto<List<String>> updateRoomListRequestBodyDto = new RequestBodyDto<List<String>>(
				"updateRoomList", roomNameList);

		server.ServerMain.connectedSocketList.forEach(con -> {
			ServerSender.getInstance().send(con.socket, updateRoomListRequestBodyDto);
			

		// 송유나 방장 유무 검사 
		if (con.username.equals(username)) {
			con.isOwner = true;
		} else { con.isOwner = false; }
		});

		
	}
    
	//방에 들어왔을 때 유저리스트와 join메시지를 반환
	private void join(String requestBody) {
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		ServerMain.getInstance().sysoutGUI("join 정보 반환");

		System.out.println("연결된 소켓리스트에 저장된 데이터를  usernameList에 저장하고 값을 옮긴다.");
		ServerMain.connectedSocketList.forEach(connectedSocket -> {
		});

		ServerMain.roomList.forEach(room -> {
			if(room.getRoomName().equals(roomName)) {
				room.getUserList().add(this);
				
				List<String> usernameList = new ArrayList<>();
				
				
				room.getUserList().forEach(con -> {
//					usernameList.add(con.username);
					usernameList.add(con.username + (con.isOwner ? " (방장)" : "")); // 송유나(방장뜨는 코드)
				});
				//System.out.println("유저리스트 업데이트 및 접속알림 데이터 생성");
				room.getUserList().forEach(connectedSocket -> {
					RequestBodyDto<List<String>> updateUserListDto
							= new RequestBodyDto<List<String>>("updateUserList", usernameList);
	
					RequestBodyDto<String> joinMessageDto
							= new RequestBodyDto<String>("showMessage", username + "님이 채팅방에 접속했습니다.");
					
					//클라이언트에게 데이터 보냄.
					//System.out.println("서버에서 send() 실행. update와 join 정보를 보낸다.");
					ServerSender.getInstance().send(connectedSocket.socket, updateUserListDto);
					try {
						Thread.sleep(100);
					} catch(InterruptedException e) {
						e.printStackTrace();
					}
					ServerSender.getInstance().send(connectedSocket.socket, joinMessageDto);

				});
				
			}
		});

	}

	//방 나갈 때 유저리스트와 exitRoom 메시지 반환
	private void exitRoom(String requestBody) { 
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		ServerMain.getInstance().sysoutGUI("exitRoom 정보 반환");
	
		ServerMain.connectedSocketList.forEach(connectedSocket -> {
		});

		server.ServerMain.roomList.forEach(room -> {
			if(room.getRoomName().equals(roomName)) {
				room.getUserList().remove(this);
				
				List<String> usernameList = new ArrayList<>();
					
				room.getUserList().forEach(con -> {
					usernameList.add(con.username + (con.isOwner ? " (방장)" : ""));
				});
				room.getUserList().forEach(connectedSocket -> {
					
					//System.out.println("유저리스트 업데이트 및 접속알림 데이터 생성");
					RequestBodyDto<List<String>> updateUserListDto
							= new RequestBodyDto<List<String>>("updateUserList", usernameList);
	
					RequestBodyDto<String> joinMessageDto
							= new RequestBodyDto<String>("showMessage", username + "님이 채팅방을 나갔습니다.");
					
					//클라이언트에게 데이터 보냄.
					//System.out.println("서버에서 send() 실행. update와 join 정보를 보낸다.");
					ServerSender.getInstance().send(connectedSocket.socket, updateUserListDto);
					try {
						Thread.sleep(100);
					} catch(InterruptedException e) {
						e.printStackTrace();
					}
					ServerSender.getInstance().send(connectedSocket.socket, joinMessageDto);

				});
				
			}
		});

	}
	
	private void ownerExitRoom(String requestBody) {
		ServerMain.getInstance().sysoutGUI("ownerExit - updateRoomList");
		
		//updateRoomList
		ServerMain.connectedSocketList.forEach(connectedSocket -> {
			System.out.println(connectedSocket);
		});
		
		List<String> roomNameList = new ArrayList<>();
		
		server.ServerMain.roomList.forEach(room -> {
			roomNameList.remove(room.getRoomName());
		});
		
		RequestBodyDto<List<String>> updateRoomListRequestBodyDto 
			= new RequestBodyDto<List<String>>("updateRoomList", roomNameList);
		server.ServerMain.connectedSocketList.forEach(con -> {
			ServerSender.getInstance().send(con.socket, updateRoomListRequestBodyDto);
		});
		
		
		//notiRoomClosure
		String roomName = (String) gson.fromJson(requestBody, RequestBodyDto.class).getBody();
		
		
		ServerMain.roomList.forEach(room -> {
			if(room.getRoomName().equals(roomName)) { //같은 방에 있는 사람들
				room.getUserList().remove(this); //리스트에서 방장을 제거
				
				//방장을 제외한 방 접속자리스트
				List<String> usernameList = new ArrayList<>(); 
				room.getUserList().forEach(con -> {
					usernameList.add(con.username);
					System.out.println(con.username);
				});
				
				room.getUserList().forEach(connectedSocket -> {
					RequestBodyDto<String> joinMessageDto
						= new RequestBodyDto<String>("notiRoomClosure", "방장이 방을 폭파했습니다");
					
					ServerSender.getInstance().send(connectedSocket.socket, joinMessageDto);
				});
			}
		});
		ServerMain.getInstance().sysoutGUI("ownerExit - updateRoomList");
		
	}
	private void sendWhisper(String requestBody) {
		
	}
	
	//메시지 입력시 모든 접속자에게 반환
	private void sendMessage(String requestBody) {
		TypeToken<RequestBodyDto<SendMessage>> typeToken = new TypeToken<>() {
		};

		// 클라이언트한테 받은 Json데이터를, SendMessage객체로 변환
		RequestBodyDto<SendMessage> requestBodyDto = gson.fromJson(requestBody, typeToken.getType());
		SendMessage sendMessage = requestBodyDto.getBody();

		server.ServerMain.roomList.forEach(room -> {
			if(room.getUserList().contains(this)) {
				// 반복으로 모든 접속자에게 전송
				room.getUserList().forEach(connectedSocket -> {
					RequestBodyDto<String> dto = new RequestBodyDto<String>("showMessage",
							sendMessage.getFromUsername() + ": " + sendMessage.getMessageBody());

					ServerSender.getInstance().send(connectedSocket.socket, dto);
				});	
			}
		});
		
		// 반복으로 모든 접속자에게 전송

		// <<< 케이스 메서드 끝 >>>
	}
}
